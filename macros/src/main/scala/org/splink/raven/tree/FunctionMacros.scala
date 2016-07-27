package org.splink.raven.tree

import scala.annotation.implicitNotFound
import scala.reflect.macros._
import scala.language.experimental.macros

case class FunctionInfo[T](fnc: T, types: List[(String, String)])
trait Fnc[T]

object FunctionMacros {
  /*
TODO applyParams (s: Seq[T] => T)
  def applyParams[T](f: T, s: Seq[T])(implicit fnc: Fnc[T]): T = {
    f.apply(s.head, s(1), s(2), s(3))
  }
  */

  implicit def materialize[T]: Fnc[T] = macro materializeImpl[T]

  def materializeImpl[T](c: whitebox.Context)(implicit tag: c.WeakTypeTag[T]): c.Expr[Fnc[T]] = {
    val fncs = (0 to 22).map { i =>
      c.universe.definitions.FunctionClass(i)
    }

    if (fncs.contains(tag.tpe.typeSymbol))
      c.universe.reify {
        new Fnc[T] {}
      }
    else {
      c.abort(c.macroApplication.pos, "Sorry, but this is not a function")
    }
  }

  @implicitNotFound("You must supply a function")
  implicit def signature[T](f: T)(implicit fnc: Fnc[T]): FunctionInfo[T] = macro signatureImpl[T]

  def signatureImpl[T](c: blackbox.Context)(f: c.Expr[T])(fnc: c.Expr[Fnc[T]])(implicit tag: c.WeakTypeTag[T]) = {
    import c.universe._

    val pairs = f.tree.filter(_.isDef).collect {
      case ValDef(_, name, typ, _) =>
        name.decodedName.toString -> typ.tpe.typeSymbol.fullName.replaceAll("\\$", "")
    }

    q"FunctionInfo[$tag]($f, $pairs)"
  }
}