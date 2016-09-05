package org.splink.raven

import java.io._
import java.util.Base64
import scala.util.{Success, Failure, Try}
import scala.reflect.runtime.universe._

trait Serializer {

  case class SerializationError(msg: String)

  def serializer: SerializerService

  trait SerializerService {
    def serialize[T <: Serializable](t: T): Either[SerializationError, String]

    def deserialize[T <: Serializable](s: String)(implicit ev: TypeTag[T]): Either[SerializationError, T]
  }

}

trait SerializerImpl extends Serializer {

  override val serializer = new SerializerService {

    override def serialize[T <: Serializable](t: T) = {
      loan(new ByteArrayOutputStream()) { bos =>
        loan(new ObjectOutputStream(bos)) { os =>
          os.writeObject(t)
          Base64.getEncoder.encodeToString(bos.toByteArray)
        }
      }.joinRight
    }

    override def deserialize[T <: Serializable](s: String)(implicit ev: TypeTag[T]) = {
      Try {
        Base64.getDecoder.decode(s)
      } match {
        case Success(decoded) =>
          loan(new ByteArrayInputStream(decoded)) { bis =>
            loan(new ObjectInputStream(bis)) { in =>
              matchType {
                in.readObject().asInstanceOf[T]
              }
            }
          }.joinRight

        case Failure(t) =>
          Left(SerializationError("serialize error: " + messageFor(t)))
      }
    }

    def messageFor(t: Throwable) = if (Option(t.getMessage).isDefined) t.getMessage else "No message"

    def matchType[T](o: T)(implicit ev: TypeTag[T]) = {
      val typeName = ev.tpe.typeSymbol.fullName.replaceAll("\\$", "")
      if (typeName != o.getClass.getCanonicalName)
        throw new RuntimeException("types do not match: " + typeName + "!= " + o.getClass.getCanonicalName)
      o
    }

    def loan[A <: Closeable, B](os: A)(handler: A => B): Either[SerializationError, B] = {
      try {
        Right(handler(os))
      } catch {
        case t: Throwable =>
          os.close()
          Left(SerializationError("serialize error: " + messageFor(t)))
      } finally {
        os.close()
      }
    }
  }
}