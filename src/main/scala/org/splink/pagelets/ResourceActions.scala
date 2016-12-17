package org.splink.pagelets

import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, Seconds}
import play.api.http.HeaderNames._
import play.api.mvc.Results._
import play.api.mvc.{Action, AnyContent, Request, Result}

import scala.concurrent.duration._

trait ResourceActions {
  def ResourceAction(fingerprint: String, validFor: Duration = 365.days): Action[AnyContent]
}

trait ResourceActionsImpl extends ResourceActions { self: Resources =>
  override def ResourceAction(fingerprint: String, validFor: Duration = 365.days) = EtagAction { _ =>
    resources.contentFor(Fingerprint(fingerprint)).map { content =>
      Ok(content.body).as(content.mimeType.name).withHeaders(CacheHeaders(fingerprint, validFor): _*)
    }.getOrElse {
      BadRequest
    }
  }

  def EtagAction(f: Request[AnyContent] => Result) = Action { request =>
    request.headers.get(IF_NONE_MATCH).map { etag =>
      if (resources.contains(Fingerprint(etag))) NotModified else f(request)
    }.getOrElse {
      f(request)
    }
  }

  val dateFormat = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  def CacheHeaders(fingerprint: String, validFor: Duration = 365.days) = {
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