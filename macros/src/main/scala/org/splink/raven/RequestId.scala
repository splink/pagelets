package org.splink.raven

object RequestId {
  private val rnd = new scala.util.Random()

  def create = RequestId("[" + (0 to 5).map { _ =>
    (rnd.nextInt(90 - 65) + 65).toChar
  }.mkString + "]")
}

case class RequestId(id: String) {
  override def toString = id
}