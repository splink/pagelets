package org.splink.raven

import play.api.mvc.PathBindable

object Binders {

  implicit object PathBindableSymbol extends PathBindable[Symbol] {
    def bind(key: String, value: String) = try {
      Right(Symbol(value))
    } catch {
      case e: Exception => Left("Cannot parse parameter '" + key + "' as Symbol")
    }

    def unbind(key: String, value: Symbol): String = value.name
  }

}