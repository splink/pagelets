package org.splink.pagelets

object Exceptions {

  class PageletException(val msg: String) extends RuntimeException(msg)

  case class TypeException(override val msg: String) extends PageletException(msg)

  case class NoFallbackException(id: Symbol) extends PageletException(s"Fallback not defined for $id")


}
