package org.splink.raven

import org.apache.commons.codec.digest.DigestUtils
import org.splink.raven.PageletResult.{Css, Javascript, Asset}
import play.api.{Logger, Mode, Environment}

import scala.io.Source

object Resources {

  private val NewLine = "\n"
  private val BasePath = "public/"
  private var cache = Map[Key, Content]()
  private var itemCache = Map[Key, Content]()

  case class Key(s: String) {
    override def toString = s
  }

  case object Content {
    val empty = Content("", PlainMimeType)
  }

  case class Content(body: String, mimeType: MimeType) {
    override def toString = body

    def +(that: Content) = copy(body = body + that.body, mimeType = that.mimeType)
  }

  case class Hashes(js: String, css: String)

  def contentFor(hash: String): Option[Content] = cache.get(Key(hash))

  def update(js: Set[Javascript], css: Set[Css])(implicit e: Environment) = {
    def hash(c: Content) = Key(DigestUtils.md5Hex(c.body))
    def mk(assets: Seq[Asset]) = {
      val content = assemble(assets)
      val hashed = hash(content)
      cache = cache + (hashed -> content)
      hashed
    }

    Hashes(mk(js.toSeq).toString, mk(css.toSeq).toString)
  }

  private def assemble(assets: Seq[Asset])(implicit e: Environment) = synchronized {
    assets.foldLeft(Content.empty) { (acc, next) =>
      val maybeContent = for {
        content <- itemCache.get(Key(next.src)) if e.mode == Mode.Prod
      } yield content

      maybeContent.map { content =>
        acc + content
      }.getOrElse {
        val text = e.resourceAsStream(BasePath + next.src).map(Source.fromInputStream(_).mkString).getOrElse {
          Logger.warn(s"Missing ${mimeTypeFor(next)} resource: ${next.src}")
          ""
        }
        val content = Content(text + NewLine, mimeTypeFor(next))
        itemCache = itemCache + (Key(next.src) -> content)
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

  private def mimeTypeFor(asset: Asset) = asset match {
    case a: Javascript => JsMimeType
    case a: Css => CssMimeType
  }
}
