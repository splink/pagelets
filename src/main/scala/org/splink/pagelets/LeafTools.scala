package org.splink.pagelets

import scala.language.implicitConversions
import akka.stream.Materializer
import org.splink.pagelets.Exceptions.TypeException
import play.api.http.HeaderNames
import play.api.mvc.{Action, AnyContent, Cookies, Request}
import scala.concurrent.{ExecutionContext, Future}

trait LeafTools {

  implicit def leafOps(leaf: Leaf[_, _]): LeafOps

  trait LeafOps {
    def execute(fi: FunctionInfo[_], args: Seq[Arg])(
      implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[PageletResult]
  }

}

trait LeafToolsImpl extends LeafTools {

  override implicit def leafOps(leaf: Leaf[_, _]): LeafOps = new LeafOpsImpl(leaf)

  class LeafOpsImpl(leaf: Leaf[_, _]) extends LeafOps {
    type R = Action[AnyContent]

    val log = play.api.Logger("LeafTools")

    case class ArgError(msg: String)

    override def execute(fi: FunctionInfo[_], args: Seq[Arg])(
      implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[PageletResult] =
      values(fi, args).fold(
        err => Future.failed(TypeException(s"${leaf.id}: ${err.msg}")), {
          case Nil =>
            fi.fnc.asInstanceOf[() => R]()
          case a :: Nil =>
            fi.fnc.asInstanceOf[Any => R](a)
          case a :: b :: Nil =>
            fi.fnc.asInstanceOf[(Any, Any) => R](a, b)
          case a :: b :: c :: Nil =>
            fi.fnc.asInstanceOf[(Any, Any, Any) => R](a, b, c)
          case a :: b :: c :: d :: Nil =>
            fi.fnc.asInstanceOf[(Any, Any, Any, Any) => R](a, b, c, d)
          case a :: b :: c :: d :: e :: Nil =>
            fi.fnc.asInstanceOf[(Any, Any, Any, Any, Any) => R](a, b, c, d, e)
          case a :: b :: c :: d :: e :: f :: Nil =>
            fi.fnc.asInstanceOf[(Any, Any, Any, Any, Any, Any) => R](a, b, c, d, e, f)
          case a :: b :: c :: d :: e :: f :: g :: Nil =>
            fi.fnc.asInstanceOf[(Any, Any, Any, Any, Any, Any, Any) => R](a, b, c, d, e, f, g)
          case a :: b :: c :: d :: e :: f :: g :: h :: Nil =>
            fi.fnc.asInstanceOf[(Any, Any, Any, Any, Any, Any, Any, Any) => R](a, b, c, d, e, f, g, h)
          case a :: b :: c :: d :: e :: f :: g :: h :: i :: Nil =>
            fi.fnc.asInstanceOf[(Any, Any, Any, Any, Any, Any, Any, Any, Any) => R](a, b, c, d, e, f, g, h, i)
          case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: Nil =>
            fi.fnc.asInstanceOf[(Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => R](a, b, c, d, e, f, g, h, i, j)
          case xs =>
            Future.failed(new IllegalArgumentException(s"${leaf.id}: too many arguments: ${xs.size}"))
        }
      )

    implicit def transform(action: Action[AnyContent])(
      implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[PageletResult] =
      action(r).map { result =>

        val cookies = result.header.headers.get(HeaderNames.SET_COOKIE).
          map(Cookies.decodeSetCookieHeader).getOrElse(Seq.empty)

        (result.body.consumeData, cookies)
      }.flatMap { case (eventualByteString, cookies) =>
        eventualByteString.map { byteString =>
          PageletResult(byteString.utf8String, leaf.javascript, leaf.javascriptTop, leaf.css, cookies, leaf.metaTags)
        }
      }

    def values[T](info: FunctionInfo[T], args: Seq[Arg]): Either[ArgError, Seq[Any]] = eitherSeq {
      def predicate(name: String, typ: String, arg: Arg) =
        name == arg.name && typ == scalaClassNameFor(arg.value)

      info.types.map { case (name, typ) =>
        args.find(arg => predicate(name, typ, arg)).map { a =>
          Right(a.value)
        }.getOrElse {
          val msg = args.map(arg => s"${arg.name}:${scalaClassNameFor(arg.value)}").mkString(",")
          Left(ArgError(s"'$name:$typ' not found in Arguments($msg)"))
        }
      }
    }

    def scalaClassNameFor(v: Any) = Option((v match {
      case x: Int => Int.getClass
      case x: Double => Double.getClass
      case x: Float => Float.getClass
      case x: Long => Long.getClass
      case x: Short => Short.getClass
      case x: Byte => Byte.getClass
      case x: Boolean => Boolean.getClass
      case x: Char => Char.getClass
      case x: Some[_] => Option.getClass
      case None => Option.getClass
      case x: Any => x.getClass
    }).getCanonicalName).map(_.replaceAll("\\$", "")).getOrElse("undefined")

    def eitherSeq[A, B](e: Seq[Either[A, B]]): Either[A, Seq[B]] =
      e.foldRight(Right(Seq.empty): Either[A, Seq[B]]) {
        (next, acc) => for (xs <- acc.right; x <- next.right) yield xs.+:(x)
      }
  }

}