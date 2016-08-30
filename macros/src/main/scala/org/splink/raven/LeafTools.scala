package org.splink.raven

import akka.stream.{ActorMaterializer, Materializer}
import org.splink.raven.Exceptions.TypeException
import play.api.http.HeaderNames
import play.api.mvc.{Action, AnyContent, Cookies, Request}

import scala.concurrent.{ExecutionContext, Future}

trait LeafTools {

  implicit def leafOps(leaf: Leaf[_, _]): LeafOps

  trait LeafOps {
    def execute(fi: FunctionInfo[_], args: Seq[Arg])(
      implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[BrickResult]
  }

}

trait LeafToolsImpl extends LeafTools {
  self: Serializer =>

  override implicit def leafOps(leaf: Leaf[_, _]): LeafOps = new LeafOpsImpl(leaf)

  class LeafOpsImpl(leaf: Leaf[_, _]) extends LeafOps {
    type R = Action[AnyContent]

    case class ArgError(msg: String)

    override def execute(fi: FunctionInfo[_], args: Seq[Arg])(
      implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[BrickResult] =
      values(fi, args: _*).fold(
        err => Future.failed(TypeException(s"$leaf.id ${err.msg}")), {
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
            throw new IllegalArgumentException(s"$leaf.id too many arguments: ${xs.size}")
        }
      )

    implicit def transform(action: Action[AnyContent])(
      implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[BrickResult] =
      action(r).map { result =>

        def header(key: String) = result.header.headers.get(key)

        def to[T](key: String, f: String => T) =
          header(key).map(_.split(",").map(f).toSet).getOrElse(Set.empty)

        val js = to(Javascript.name, Javascript.apply)
        val jsTop = to(Javascript.nameTop, Javascript.apply)
        val css = to(Css.name, Css.apply)
        val metaTags = header(MetaTag.name).map(_.split("\n").map(serializer.deserialize[MetaTag]).toSet).getOrElse(Set.empty)
        val cookies = header(HeaderNames.SET_COOKIE).map(Cookies.decodeSetCookieHeader).getOrElse(Seq.empty)
        (result.body.consumeData, js, jsTop, css, cookies, metaTags)
      }.flatMap { case (eventualByteString, js, jsTop, css, cookies, metaTags) =>
        eventualByteString.map { byteString =>
          BrickResult(byteString.utf8String, js, jsTop, css, cookies, metaTags)
        }
      }

    def values[T](info: FunctionInfo[T], args: Arg*): Either[ArgError, Seq[Any]] = eitherSeq {
      def predicate(name: String, typ: String, arg: Arg) =
        name == arg.name && typ == scalaClassNameFor(arg.value)

      info.types.map { case (name, typ) =>
        args.find(arg => predicate(name, typ, arg)).map { p =>
          Right(p.value)
        }.getOrElse {
          val msg = args.map(arg => s"${arg.name}:${scalaClassNameFor(arg.value)}").mkString(",")
          Left(ArgError(s"'$name:$typ' not found in Arguments($msg)"))
        }
      }
    }

    def scalaClassNameFor(v: Any) = (v match {
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

    def eitherSeq[A, B](e: Seq[Either[A, B]]) =
      e.foldRight(Right(Seq.empty): Either[A, Seq[B]]) {
        (e, acc) => for (xs <- acc.right; x <- e.right) yield xs.+:(x)
      }
  }

}