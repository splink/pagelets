package org.splink.pagelets

sealed trait Resource {
  def src: String
}

object Javascript {
  val name: String = "js"
  val nameTop: String = "jsTop"
}

case class Javascript(src: String) extends Resource

object Css {
  val name: String = "css"
}

case class Css(src: String) extends Resource

object MetaTag {
  val name: String = "meta"
}

case class MetaTag(name: String, content: String)
