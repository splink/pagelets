package org.splink.pagelets

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.time.temporal.ChronoUnit

import play.api.mvc._

import scala.concurrent.duration._

trait ResourceActions {
  def ResourceAction(fingerprint: String, validFor: Duration = 365.days): Action[AnyContent]
}

trait ResourceActionsImpl extends ResourceActions { self: Resources with BaseController =>
  override def ResourceAction(fingerprint: String, validFor: Duration = 365.days) = EtagAction { _ =>
    resources.contentFor(Fingerprint(fingerprint)).map { content =>
      Ok(content.body).as(content.mimeType.name).withHeaders(CacheHeaders(fingerprint, validFor): _*)
    }.getOrElse {
      BadRequest
    }
  }

  def EtagAction(f: Request[AnyContent] => Result) = Action { request =>
    request.headers.get(IF_NONE_MATCH).map { etag =>
      if (resources.contains(Fingerprint(etag.replaceAll(""""""", "")))) NotModified else f(request)
    }.getOrElse {
      f(request)
    }
  }

  def CacheHeaders(fingerprint: String, validFor: Duration = 365.days) = {
    def format(zdt: ZonedDateTime) =
      DateTimeFormatter.RFC_1123_DATE_TIME.format(zdt)

    val now = ZonedDateTime.now(ZoneId.of("GMT"))
    val future = now.plusDays(validFor.toDays)

    def elapsed = ChronoUnit.SECONDS.between(now, future)

    Seq(
      DATE -> format(now),
      LAST_MODIFIED -> format(now),
      EXPIRES -> format(future),
      ETAG -> s""""$fingerprint"""",
      CACHE_CONTROL -> s"public, max-age: ${elapsed.toString}")
  }

}