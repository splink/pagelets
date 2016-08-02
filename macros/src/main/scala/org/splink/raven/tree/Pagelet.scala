package org.splink.raven.tree

import akka.stream.Materializer
import org.splink.raven.tree.PageletResult.{Css, Javascript}
import play.api.http.{ContentTypeOf, ContentTypes, Writeable}
import play.api.mvc.{Action, AnyContent, Request, Result, Codec}

import scala.concurrent.{ExecutionContext, Future}

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

case class Tree(id: PageletId, children: Seq[Pagelet], combine: Seq[PageletResult] => PageletResult = PageletResult.combine) extends Pagelet

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

case class Leaf[A, B](id: PageletId,
                      private val f: FunctionInfo[A],
                      private val fallback: Option[FunctionInfo[B]] = None) extends Pagelet {
  type R = Action[AnyContent]

  def withFallback(fallback: FunctionInfo[B]) = Leaf(id, f, Some(fallback))

  import Leaf._

  def run(args: Arg*)(implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[PageletResult] =
    execute(f, args)

  def runFallback(args: Arg*)(implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[PageletResult] =
    fallback.map(execute(_, args)).getOrElse {
      Future.failed(NoFallbackDefinedException(id))
    }

  private def execute(f: FunctionInfo[_], args: Seq[Arg])(implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[PageletResult] =
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

  private implicit def transform(action: Action[AnyContent])(implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[PageletResult] =
    action(r).map { result =>

      def to[T](key: String, f: String => T) =
        result.header.headers.get(key).map(_.split(",").map(f).toSet).getOrElse(Set.empty)

      (result.body.consumeData, to(Javascript.name, Javascript.apply), to(Css.name, Css.apply))
    }.flatMap { case (eventualByteString, js, css) =>
      eventualByteString.map { byteString =>
        PageletResult(byteString.utf8String, js, css)
      }
    }
}

object PageletResult {

  case object Javascript {
    val name: String = "js"
  }

  case class Javascript(src: String)

  case object Css {
    val name: String = "css"
  }

  case class Css(src: String)

  val empty = PageletResult("")

  def combine(results: Seq[PageletResult]): PageletResult = results.reduce { (acc, next) =>
    PageletResult(acc.body + next.body, acc.js ++ next.js, acc.css ++ next.css)
  }

  def combineAssets(results: Seq[PageletResult]): (String) => PageletResult = {
    val (js, css) = results.foldLeft(Set.empty[Javascript], Set.empty[Css]) { (acc, next) =>
      (acc._1 ++ next.js, acc._2 ++ next.css)
    }
    PageletResult(_, js, css)
  }

  implicit class ResultOps(result: Result) {
    def withJavascript(js: Javascript*) = helper(js.map(_.src), Javascript.name)

    def withCss(css: Css*) = helper(css.map(_.src), Css.name)

    private def helper(elems: Seq[String], id: String) =
      result.withHeaders(s"$id" -> elems.mkString(","))
  }

  implicit val ct = ContentTypeOf[PageletResult](Some(ContentTypes.HTML))

  implicit def writeableOf(implicit codec: Codec, ct: ContentTypeOf[PageletResult]): Writeable[PageletResult] =
    Writeable(result => codec.encode(result.body.trim))
}

case class PageletResult(body: String, js: Set[Javascript] = Set.empty, css: Set[Css] = Set.empty)