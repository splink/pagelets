package org.splink.pagelets

import play.api.mvc._

trait Pagelets
  extends BaseController
    with PageletActions
    with PageBuilder
    with ResourceActions
    with Visualizer
    with TreeTools {

  import scala.language.experimental.macros

  implicit def materialize[T]: Fnc[T] = macro FunctionMacros.materializeImpl[T]

  implicit def signature[T](f: T)(implicit fnc: Fnc[T]): FunctionInfo[T] = macro FunctionMacros.signatureImpl[T]

  implicit class PageletIdOps(s: String) {
    def id: PageletId = PageletId(s)
  }
}
