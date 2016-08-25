package org.splink.raven

import org.apache.commons.codec.digest.DigestUtils
import org.splink.raven.BrickResult.{Css, Javascript, Resource}
import play.api.mvc.PathBindable
import play.api.{Logger, Mode, Environment}

import scala.io.Source

object Resource {
  private val logger = Logger(Resource.getClass).logger

  private val NewLine = "\n"
  private val BasePath = "public/"
  private var cache = Map[Fingerprint, Content]()
  private var itemCache = Map[Fingerprint, Content]()

  case object Fingerprint {
    implicit def pathBinder(implicit intBinder: PathBindable[Fingerprint]) = new PathBindable[Fingerprint] {
      override def bind(key: String, value: String): Either[String, Fingerprint] = Right(Fingerprint(value))

      override def unbind(key: String, user: Fingerprint): String = user.toString
    }
  }

  case class Fingerprint(s: String) {
    override def toString = s
  }

  case object Content {
    val empty = Content("", PlainMimeType)
  }

  case class Content(body: String, mimeType: MimeType) {
    override def toString = body

    def +(that: Content) = copy(body = body + that.body, mimeType = that.mimeType)
  }

  def contentFor(fingerprint: Fingerprint): Option[Content] = cache.get(fingerprint)

  def contains(fingerprint: Fingerprint): Boolean = cache.contains(fingerprint)

  def update[T <: Resource](r: Set[T])(implicit e: Environment) = {
    def fingerprint(c: Content) = Fingerprint(DigestUtils.md5Hex(c.body))
    def mk(assets: Seq[Resource]) = {
      if (assets.nonEmpty) {
        val content = assemble(assets)
        val hash = fingerprint(content)
        cache = cache + (hash -> content)
        Some(hash)
      } else None
    }

    mk(r.toSeq)
  }

  private def assemble(assets: Seq[Resource])(implicit e: Environment) = synchronized {
    assets.foldLeft(Content.empty) { (acc, next) =>
      val maybeContent = for {
        content <- itemCache.get(Fingerprint(next.src)) if e.mode == Mode.Prod
      } yield content

      maybeContent.map { content =>
        acc + content
      }.getOrElse {
        val text = e.resourceAsStream(BasePath + next.src).map(Source.fromInputStream(_).mkString).getOrElse {
          logger.warn(s"Missing ${mimeTypeFor(next)} resource: ${next.src}")
          ""
        }
        val content = Content(text + NewLine, mimeTypeFor(next))
        itemCache = itemCache + (Fingerprint(next.src) -> content)
        acc + content
      }
    }
  }

  sealed trait MimeType {
    def name: String
  }

  case object PlainMimeType extends MimeType {
    override def name = "plain/text"
  }

  case object CssMimeType extends MimeType {
    override def name = "text/css"
  }

  case object JsMimeType extends MimeType {
    override def name = "text/javascript"
  }

  private def mimeTypeFor(asset: Resource) = asset match {
    case a: Javascript => JsMimeType
    case a: Css => CssMimeType
  }
}
