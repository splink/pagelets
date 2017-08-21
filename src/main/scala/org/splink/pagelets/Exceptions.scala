package org.splink.pagelets

object Exceptions {

  class PageletException(val msg: String) extends RuntimeException(msg)

  case class TypeException(override val msg: String) extends PageletException(msg)

}
