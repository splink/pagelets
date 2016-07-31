package org.splink.raven.tree

import akka.stream.Materializer
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}


case object Html {
  def empty = Html("")

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

case class NoFallbackDefinedException(id: PageletId) extends RuntimeException(s"Fallback not defined for ${id.toString}")

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

case class Leaf[A, B](id: PageletId, private val f: FunctionInfo[A], private val fallback: Option[FunctionInfo[B]] = None) extends Pagelet {
  type R = Action[AnyContent]

  def withFallback(fallback: FunctionInfo[B]) = Leaf(id, f, Some(fallback))

  import Leaf._

  def run(args: Arg*)(implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[Html] =
    execute(f, args)

  def runFallback(args: Arg*)(implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[Html] =
    fallback.map(execute(_, args)).getOrElse {
      Future.failed(NoFallbackDefinedException(id))
    }

  private def execute(f: FunctionInfo[_], args: Seq[Arg])(implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[Html] =
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

  private implicit def transform(action: Action[AnyContent])(implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[Html] =
    action(r).flatMap { result =>
      result.body.consumeData
    }.map(s => Html(s.utf8String))

}

object PageletResult {

  case class Javascript(src: String)

  case class Css(src: String)

  implicit class ResultOps(result: Result) {
    def withJavascript(js: Javascript*) = helper(js.map(_.src), "js")

    def withCss(css: Css*) = helper(css.map(_.src), "css")

    private def helper(elems: Seq[String], id: String) =
      result.withHeaders(elems.zipWithIndex.map { case (elem, index) =>
        s"$id-$index" -> elem
      }: _*)
  }

}