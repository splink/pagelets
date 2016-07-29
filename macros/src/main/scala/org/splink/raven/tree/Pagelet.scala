package org.splink.raven.tree

import akka.stream.Materializer
import play.api.Logger
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random


case object Html {
  def combine(htmls: Html*): Html = htmls.reduce((a, b) => Html(a.body + b.body))
}

case class Html(body: String)

object Util {
  def eitherSeq[A, B](e: Seq[Either[A, B]]) =
    e.foldRight(Right(Seq.empty): Either[A, Seq[B]]) {
      (e, acc) => for (xs <- acc.right; x <- e.right) yield xs.+:(x)
    }
}

case class TypeException(msg: String) extends RuntimeException(msg)

case class TypeError(msg: String)

trait PageletId

case class Arg(name: String, value: Any)

sealed trait Pagelet {
  def id: PageletId
}

case class Tree(id: PageletId, children: Seq[Pagelet], combine: Seq[Html] => Html = Html.combine) extends Pagelet

case object Leaf {
  protected def values[T](info: FunctionInfo[T], args: Arg*): Either[TypeError, Seq[Any]] = Util.eitherSeq {
    def predicate(name: String, typ: String, arg: Arg) =
      name == arg.name && typ == scalaClassNameFor(arg.value)

    info.types.map { case (name, typ) =>
      args.find(arg => predicate(name, typ, arg)).map { p =>
        Right(p.value)
      }.getOrElse {
        val argsString = args.map(arg => s"${arg.name}:${scalaClassNameFor(arg.value)}").mkString(",")
        Left(TypeError(s"'$name:$typ' not found in Arguments($argsString)"))
      }
    }
  }

  private def scalaClassNameFor(v: Any) = (v match {
    case x: Int => Int.getClass
    case x: Double => Double.getClass
    case x: Float => Float.getClass
    case x: Long => Long.getClass
    case x: Short => Short.getClass
    case x: Byte => Byte.getClass
    case x: Boolean => Boolean.getClass
    case x: Char => Char.getClass
    case x: Any => x.getClass
  }).getCanonicalName.replaceAll("\\$", "")
}

case class Leaf[A](id: PageletId, private val f: FunctionInfo[A]) extends Pagelet {
  type R = Action[AnyContent]

  import Leaf._

  def exec(args: Arg*)(implicit ec: ExecutionContext, r: PageletRequest[AnyContent], m: Materializer): Future[Html] =
    values(f, args: _*).fold(
      err => Future.failed(TypeException(s"$id ${err.msg}")), {
        case Nil =>
          f.fnc.asInstanceOf[() => R]()
        case a :: Nil =>
          f.fnc.asInstanceOf[Any => R](a)
        case a :: b :: Nil =>
          f.fnc.asInstanceOf[(Any, Any) => R](a, b)
        case a :: b :: c :: Nil =>
          f.fnc.asInstanceOf[(Any, Any, Any) => R](a, b, c)
        case a :: b :: c :: d :: Nil =>
          f.fnc.asInstanceOf[(Any, Any, Any, Any) => R](a, b, c, d)
        case xs =>
          throw new IllegalArgumentException(s"$id too many arguments: ${xs.size}")
      }
    )


  private implicit def transform(action: Action[AnyContent])(implicit ec: ExecutionContext, r: PageletRequest[AnyContent], m: Materializer): Future[Html] =
    action(r).flatMap { result =>
      result.body.consumeData
    }.map(s => Html(s.utf8String))
}


class PageletRequest[T](request: Request[T], val requestId: String, m: Map[String, Any] = Map.empty) extends WrappedRequest(request) {
  def withPageletId(id: PageletId) = new PageletRequest[T](request, requestId, m + ("pageletId" -> id))

  def pageletId = m.getOrElse("pageletId", "Root")
}

object PageletAction extends ActionBuilder[PageletRequest] {
  override def invokeBlock[A](request: Request[A], block: PageletRequest[A] => Future[Result]): Future[Result] = {
    implicit val ec = executionContext

    val start = System.currentTimeMillis()

    val (requestId, pageletId, eventualResult) = request match {
      case r: PageletRequest[A] =>
        Logger.info(s"${r.requestId} Invoke pagelet ${r.pageletId}")
        (r.requestId, r.pageletId, block(r))
      case r: Request[A] =>
        val requestId = uniqueString
        Logger.info(s"$requestId Invoke page ${r.uri}")
        val pageletRequest = new PageletRequest[A](r, requestId)
        (requestId, pageletRequest.pageletId, block(pageletRequest))
    }

    eventualResult.map { result =>
      Logger.info(s"$requestId Finish pagelet $pageletId took ${System.currentTimeMillis() - start} ms")
      result
    }.recover {
      case t: Throwable =>
        Logger.error(s"Error in pagelet: $t")
        throw t
    }
  }

  val rnd = new Random()

  def uniqueString = (0 to 5).map { _ => (rnd.nextInt(90 - 65) + 65).toChar }.mkString

}

