package org.splink.pagelets

import org.splink.pagelets.Exceptions.{PageletException, TypeException}
import play.api.mvc._

trait LeafTools {

  def leafService: LeafService

  trait LeafService {
    def execute(id: Symbol, fi: FunctionInfo[_], args: Seq[Arg]): Either[PageletException, Action[AnyContent]]
  }

}
trait LeafToolsImpl extends LeafTools {

  override def leafService: LeafService = new LeafServiceImpl

  class LeafServiceImpl extends LeafService {
    type R = Action[AnyContent]

    val log = play.api.Logger("LeafTools")

    case class ArgError(msg: String)

    override def execute(id: Symbol, fi: FunctionInfo[_], args: Seq[Arg]): Either[PageletException, Action[AnyContent]] =
      values(fi, args).fold(
        err => Left(TypeException(s"$id: ${err.msg}")), {
          case Nil =>
            Right(fi.fnc.asInstanceOf[() => R]())
          case a :: Nil =>
            Right(fi.fnc.asInstanceOf[Any => R](a))
          case a :: b :: Nil =>
            Right(fi.fnc.asInstanceOf[(Any, Any) => R](a, b))
          case a :: b :: c :: Nil =>
            Right(fi.fnc.asInstanceOf[(Any, Any, Any) => R](a, b, c))
          case a :: b :: c :: d :: Nil =>
            Right(fi.fnc.asInstanceOf[(Any, Any, Any, Any) => R](a, b, c, d))
          case a :: b :: c :: d :: e :: Nil =>
            Right(fi.fnc.asInstanceOf[(Any, Any, Any, Any, Any) => R](a, b, c, d, e))
          case a :: b :: c :: d :: e :: f :: Nil =>
            Right(fi.fnc.asInstanceOf[(Any, Any, Any, Any, Any, Any) => R](a, b, c, d, e, f))
          case a :: b :: c :: d :: e :: f :: g :: Nil =>
            Right(fi.fnc.asInstanceOf[(Any, Any, Any, Any, Any, Any, Any) => R](a, b, c, d, e, f, g))
          case a :: b :: c :: d :: e :: f :: g :: h :: Nil =>
            Right(fi.fnc.asInstanceOf[(Any, Any, Any, Any, Any, Any, Any, Any) => R](a, b, c, d, e, f, g, h))
          case a :: b :: c :: d :: e :: f :: g :: h :: i :: Nil =>
            Right(fi.fnc.asInstanceOf[(Any, Any, Any, Any, Any, Any, Any, Any, Any) => R](a, b, c, d, e, f, g, h, i))
          case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: Nil =>
            Right(fi.fnc.asInstanceOf[(Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => R](a, b, c, d, e, f, g, h, i, j))
          case xs =>
            Left(TypeException(s"$id: too many arguments: ${xs.size}"))
        }
      )

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
      case _: Int => Int.getClass
      case _: Double => Double.getClass
      case _: Float => Float.getClass
      case _: Long => Long.getClass
      case _: Short => Short.getClass
      case _: Byte => Byte.getClass
      case _: Boolean => Boolean.getClass
      case _: Char => Char.getClass
      case _: Some[_] => Option.getClass
      case None => Option.getClass
      case x: Any => x.getClass
    }).getCanonicalName).map(_.replaceAll("\\$", "")).getOrElse("undefined")

    def eitherSeq[A, B](e: Seq[Either[A, B]]): Either[A, Seq[B]] =
      e.foldRight(Right(Seq.empty): Either[A, Seq[B]]) {
        (next, acc) => for (xs <- acc.right; x <- next.right) yield xs.+:(x)
      }
  }

}