package org.splink.raven

import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, Seconds}
import org.splink.raven.Resources.Fingerprint
import play.api.http.HeaderNames._
import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent.duration._

object ResourceAction {
  def apply(fingerprint: String, validFor: Duration = 365.days) = EtagAction { request =>
    Resources.contentFor(Fingerprint(fingerprint)).map { content =>
      Ok(content.body).as(content.mimeType.name).withHeaders(CacheHeaders(fingerprint, validFor): _*)
    }.getOrElse {
      BadRequest
    }
  }

  private def EtagAction(f: Request[AnyContent] => Result) = Action { request =>
    request.headers.get(IF_NONE_MATCH).map { etag =>
      if (Resources.contains(Fingerprint(etag))) NotModified else f(request)
    }.getOrElse {
      f(request)
    }
  }

  private val dateFormat = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  private def CacheHeaders(fingerprint: String, validFor: Duration = 365.days) = {
    val now = new DateTime()
    val future = now.plusYears(1)
    val diff = Seconds.secondsBetween(now, future).getSeconds

    Seq(
      DATE -> dateFormat.print(now),
      LAST_MODIFIED -> dateFormat.print(now),
      EXPIRES -> dateFormat.print(future),
      ETAG -> fingerprint,
      CACHE_CONTROL -> s"public, max-age: ${diff.toString}")
  }
}