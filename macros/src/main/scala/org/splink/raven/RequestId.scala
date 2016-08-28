package org.splink.raven

object RequestId {
  private val rnd = new scala.util.Random()

  def mkRequestId = RequestId((0 to 6).map { _ =>
    (rnd.nextInt(90 - 65) + 65).toChar
  }.mkString)
}

case class RequestId(id: String) {
  override def toString = id
}