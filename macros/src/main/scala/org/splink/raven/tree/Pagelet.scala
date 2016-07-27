package org.splink.raven.tree

import akka.stream.Materializer
import play.api.mvc.{Request, Action, AnyContent}

import scala.concurrent.{ExecutionContext, Future}


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

case class PageletId(id: String)

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

  def exec(args: Arg*)(implicit ec: ExecutionContext, r: Request[_], m: Materializer): Future[Html] =
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


  implicit def transform(action: Action[AnyContent])(implicit ec: ExecutionContext, r: Request[_], m: Materializer): Future[Html] =
    action(r).run().flatMap(_.body.consumeData).map(s => Html(s.utf8String))
}


