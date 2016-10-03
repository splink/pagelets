package org.splink.pagelets


import play.api.mvc.Action

import scala.annotation.implicitNotFound
import scala.language.experimental.macros
import scala.reflect.macros._

case class FunctionInfo[T](fnc: T, types: List[(String, String)] = Nil)
trait Fnc[T]

object FunctionMacros {

  implicit def materialize[T]: Fnc[T] = macro materializeImpl[T]

  def materializeImpl[T](c: whitebox.Context)(implicit tag: c.WeakTypeTag[T]): c.Expr[Fnc[T]] = {
    val fncs = (0 to 22).map { i =>
      c.universe.definitions.FunctionClass(i)
    }

    if (fncs.contains(tag.tpe.typeSymbol)) {
      c.universe.reify {
        new Fnc[T] {}
      }
    } else {
      c.abort(c.macroApplication.pos, "Sorry, but this is not a function")
    }
  }

  @implicitNotFound("You must supply a function")
  implicit def signature[T](f: T)(implicit fnc: Fnc[T]): FunctionInfo[T] = macro signatureImpl[T]

  def signatureImpl[T](c: blackbox.Context)(f: c.Expr[T])(fnc: c.Expr[Fnc[T]])(implicit tag: c.WeakTypeTag[T]) = {
    import c.universe._

    if(!tag.tpe.contains(typeOf[Action[_]].typeSymbol)) {
      c.abort(c.macroApplication.pos, "Sorry, but you need to provide a function which returns Action[_]")
    }

    val pairs = f.tree.filter(_.isDef).collect {
      case ValDef(_, name, typ, _) =>
        name.decodedName.toString -> typ.tpe.typeSymbol.fullName.replaceAll("\\$", "")
    }

    q"FunctionInfo[$tag]($f, $pairs)"
  }
}