package org.splink.raven

import akka.stream.Materializer
import org.splink.raven.PageletResult.{Css, Javascript}
import play.api.http.{HeaderNames, ContentTypeOf, ContentTypes, Writeable}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

private object Util {
  def eitherSeq[A, B](e: Seq[Either[A, B]]) =
    e.foldRight(Right(Seq.empty): Either[A, Seq[B]]) {
      (e, acc) => for (xs <- acc.right; x <- e.right) yield xs.+:(x)
    }
}

class PageletException(msg: String) extends RuntimeException(msg)

case class TypeException(msg: String) extends PageletException(msg)

case class NoFallbackException(id: PageletId) extends PageletException(s"Fallback not defined for ${id.toString}")

case class ArgError(msg: String)

trait PageletId

case class Arg(name: String, value: Any)

sealed trait Pagelet {
  def id: PageletId
}

case object Tree {
  def combine(results: Seq[PageletResult]): PageletResult = results.foldLeft(PageletResult.empty) { (acc, next) =>
    PageletResult(acc.body + next.body, acc.js ++ next.js, acc.css ++ next.css, acc.cookies ++ next.cookies)
  }
}

case class Tree(id: PageletId, children: Seq[Pagelet], combine: Seq[PageletResult] => PageletResult = Tree.combine) extends Pagelet {
  self =>

  def skip(id: PageletId) = {
    def f = Action(Results.Ok)
    replace(id, Leaf(id, FunctionInfo(f _, Nil)))
  }

  def replace(id: PageletId, other: Pagelet): Tree = {
    def rec(p: Pagelet): Pagelet = p match {
      case b@Tree(_, childs, _) if childs.exists(_.id == id) =>
        val idx = childs.indexWhere(_.id == id)
        b.copy(children = childs.updated(idx, other))

      case b@Tree(_, childs, _) =>
        b.copy(children = childs.map(rec))

      case pagelet =>
        pagelet
    }

    if (id == self.id) {
      other match {
        case t: Tree => t
        case l: Leaf[_, _] => Tree(id, Seq(l), combine)
      }
    } else {
      rec(this).asInstanceOf[Tree]
    }
  }

  def find(id: String): Option[Pagelet] = {
    def rec(p: Pagelet): Option[Pagelet] = p match {
      case _ if p.id.toString == id => Some(p)
      case Tree(_, children_, _) => children_.flatMap(rec).headOption
      case _ => None
    }
    rec(this)
  }
}

case object Leaf {
  private def values[T](info: FunctionInfo[T], args: Arg*): Either[ArgError, Seq[Any]] = Util.eitherSeq {
    def predicate(name: String, typ: String, arg: Arg) =
      name == arg.name && typ == scalaClassNameFor(arg.value)

    info.types.map { case (name, typ) =>
      args.find(arg => predicate(name, typ, arg)).map { p =>
        Right(p.value)
      }.getOrElse {
        val argsString = args.map(arg => s"${arg.name}:${scalaClassNameFor(arg.value)}").mkString(",")
        Left(ArgError(s"'$name:$typ' not found in Arguments($argsString)"))
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

case class Leaf[A, B](id: PageletId,
                      private val info: FunctionInfo[A],
                      private val fallback: Option[FunctionInfo[B]] = None) extends Pagelet {
  type R = Action[AnyContent]

  def withFallback(fallback: FunctionInfo[B]) = copy(fallback = Some(fallback))

  import Leaf._

  def run(args: Arg*)(implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[PageletResult] =
    execute(info, args)

  def runFallback(args: Arg*)(implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[PageletResult] =
    fallback.map(execute(_, args)).getOrElse {
      Future.failed(NoFallbackException(id))
    }

  private def execute(fi: FunctionInfo[_], args: Seq[Arg])(
    implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[PageletResult] =
    values(fi, args: _*).fold(
      err => Future.failed(TypeException(s"$id ${err.msg}")), {
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
          throw new IllegalArgumentException(s"$id too many arguments: ${xs.size}")
      }
    )

  private implicit def transform(action: Action[AnyContent])(
    implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[PageletResult] =
    action(r).map { result =>

      def to[T](key: String, f: String => T) =
        result.header.headers.get(key).map(_.split(",").map(f).toSet).getOrElse(Set.empty)

      val js = to(Javascript.name, Javascript.apply)
      val css = to(Css.name, Css.apply)
      val cookies = result.header.headers.get(HeaderNames.SET_COOKIE).map(Cookies.decodeSetCookieHeader).getOrElse(Seq.empty)

      (result.body.consumeData, js, css, cookies)
    }.flatMap { case (eventualByteString, js, css, cookies) =>
      eventualByteString.map { byteString =>
        PageletResult(byteString.utf8String, js, css, cookies)
      }
    }
}

object PageletResult {

  sealed trait Resource {
    def src: String
  }

  case object Javascript {
    val name: String = "js"
  }

  case class Javascript(src: String) extends Resource

  case object Css {
    val name: String = "css"
  }

  case class Css(src: String) extends Resource

  val empty = PageletResult("")

  implicit class ResultOps(result: Result) {
    def withJavascript(js: Javascript*) = helper(js.map(_.src), Javascript.name)

    def withCss(css: Css*) = helper(css.map(_.src), Css.name)

    private def helper(elems: Seq[String], id: String) =
      result.withHeaders(s"$id" -> elems.mkString(","))
  }

  implicit val ct: ContentTypeOf[PageletResult] =
    ContentTypeOf[PageletResult](Some(ContentTypes.HTML))

  implicit def writeableOf(implicit codec: Codec, ct: ContentTypeOf[PageletResult]): Writeable[PageletResult] =
    Writeable(result => codec.encode(result.body.trim))
}

case class PageletResult(body: String,
                         js: Set[Javascript] = Set.empty,
                         css: Set[Css] = Set.empty,
                         cookies: Seq[Cookie] = Seq.empty)