package org.splink.pagelets

import java.text.SimpleDateFormat
import java.util.{Calendar, Date, TimeZone}

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
    val format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
    format.setTimeZone(TimeZone.getTimeZone("GMT"))

    val now = new Date()
    val futureDate = Calendar.getInstance()
    futureDate.add(Calendar.DATE, validFor.toDays.toInt)

    val diff = (futureDate.getTimeInMillis - now.getTime) / 1000

    Seq(
      DATE -> format.format(now),
      LAST_MODIFIED -> format.format(now),
      EXPIRES -> format.format(futureDate.getTime),
      ETAG -> s""""$fingerprint"""",
      CACHE_CONTROL -> s"public, max-age: ${diff.toString}")
  }

}