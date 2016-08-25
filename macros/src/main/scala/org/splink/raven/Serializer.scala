package org.splink.raven

import java.io._
import java.util.Base64

object Serializer {

  def serialize[T <: Serializable](t: T): String = {
    val bos = new ByteArrayOutputStream()
    val oo = new ObjectOutputStream(bos)
    oo.writeObject(t)
    oo.close()
    bos.close()
    Base64.getEncoder.encodeToString(bos.toByteArray)
  }

  def deserialize[T <: Serializable](s: String): T = {
    val bytes = Base64.getDecoder.decode(s)
    val bis = new ByteArrayInputStream(bytes)
    val oi = new ObjectInputStream(bis)
    val obj = oi.readObject().asInstanceOf[T]
    bis.close()
    oi.close()
    obj
  }
}