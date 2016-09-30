package org.splink.raven

import org.apache.commons.codec.digest.DigestUtils
import play.api.{Environment, Logger, Mode}

import scala.io.Source

//TODO running scalatest in parallel shows the flaw in this design. maybe rework to a cake mixin is better
object Resources {
  private val impl = new ResourceProviderImpl(Map.empty, Map.empty)

  def apply(): ResourceProvider = impl

  trait ResourceProvider {
    def contains(fingerprint: Fingerprint): Boolean

    def contentFor(fingerprint: Fingerprint): Option[ResourceContent]

    def update[T <: Resource](resources: Set[T])(implicit e: Environment): Option[Fingerprint]

    def clear(): Unit
  }

  class ResourceProviderImpl(
                              var cache: Map[Fingerprint, ResourceContent],
                              var itemCache: Map[Fingerprint, ResourceContent]) extends ResourceProvider {
    val log = Logger("Resources").logger

    val BasePath = "public/"

    override def contentFor(fingerprint: Fingerprint) = cache.get(fingerprint)

    override def contains(fingerprint: Fingerprint) = cache.contains(fingerprint)

    override def clear() = {
      cache = cache.empty
      itemCache = cache.empty
    }

    override def update[T <: Resource](resources: Set[T])(implicit e: Environment) = synchronized {
      if (resources.nonEmpty) {
        val content = assemble(resources)
        val hash = Fingerprint(DigestUtils.md5Hex(content.body))
        cache = cache + (hash -> content)
        Some(hash)
      } else None
    }

    def assemble[T <: Resource](resources: Set[T])(implicit e: Environment) = {
      resources.foldLeft(ResourceContent.empty) { (acc, next) =>
        maybeCachedContent(next).map { content =>
          acc + content
        }.getOrElse {
          load(next).map { content =>
            itemCache = itemCache + (Fingerprint(next.src) -> content)
            acc + content
          }.getOrElse {
            log.warn(s"Missing ${mimeTypeFor(next)} resource: ${next.src}")
            acc
          }
        }
      }
    }

    def maybeCachedContent(resource: Resource)(implicit e: Environment) = for {
      content <- itemCache.get(Fingerprint(resource.src)) if e.mode == Mode.Prod
    } yield content

    def load(resource: Resource)(implicit e: Environment) = {
      log.debug(s"Load resource '${BasePath + resource.src}'")
      e.resourceAsStream(BasePath + resource.src).map(Source.fromInputStream(_).mkString).map { text =>
        ResourceContent(text + "\n", mimeTypeFor(resource))
      }
    }
  }

  case class Fingerprint(s: String) {
    override def toString = s
  }

  case object ResourceContent {
    val empty = ResourceContent("", PlainMimeType)
  }

  case class ResourceContent(body: String, mimeType: MimeType) {
    override def toString = body

    def +(that: ResourceContent) = copy(body = body + that.body, mimeType = that.mimeType)
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

  private def mimeTypeFor(resource: Resource) = resource match {
    case a: Javascript => JsMimeType
    case a: Css => CssMimeType
  }
}
