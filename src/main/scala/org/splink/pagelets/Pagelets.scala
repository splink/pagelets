package org.splink.pagelets

import play.api.mvc._

trait Pagelets
  extends Controller
    with PageletActions
    with PageBuilder
    with ResourceActions
    with Visualizer
    with TreeTools
    with ResultTools {

  import scala.language.experimental.macros

  implicit def materialize[T]: Fnc[T] = macro FunctionMacros.materializeImpl[T]

  implicit def signature[T](f: T)(implicit fnc: Fnc[T]): FunctionInfo[T] = macro FunctionMacros.signatureImpl[T]
}