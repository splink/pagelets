package org.splink.raven

import org.scalatest.BeforeAndAfter
import org.scalatestplus.play._
import org.splink.raven.Resources.JsMimeType
import play.api.Environment
import play.api.http.HeaderNames
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._

class ResourceActionsTest extends PlaySpec with Results with BeforeAndAfter {
  val resourceService = new ResourceActionsImpl {}.resourceService

  implicit val env = Environment.simple()
  val request = FakeRequest()

  before {
    Resources().clear()
  }

  "ResourceAction" should {
    "return the resource with status Ok for a known fingerprint" in {
      Resources().update(Set(Javascript("a.js")))
      val result = resourceService.ResourceAction("73d5636237ffec432c61a75fe9335015")(request)

      status(result) must equal(OK)
      contentType(result) must equal(Some(JsMimeType.name))
      contentAsString(result) must equal(
        """console.log("a");
          |""".stripMargin)
    }

    "return BadRequest if the fingerprint is unknown" in {
      val result = resourceService.ResourceAction("something")(request)
      status(result) must equal(BAD_REQUEST)
    }

    "return headers with etag" in {
      Resources().update(Set(Javascript("a.js")))
      val result = resourceService.ResourceAction("73d5636237ffec432c61a75fe9335015")(request)

      header(HeaderNames.ETAG, result) must equal(Some("73d5636237ffec432c61a75fe9335015"))
    }

    "return NotModified if the server holds a resource for the fingerprint in the etag (IF_NONE_MATCH) header" in {
      Resources().update(Set(Javascript("a.js")))

      val rwh = request.withHeaders(HeaderNames.IF_NONE_MATCH -> "73d5636237ffec432c61a75fe9335015")
      val result = resourceService.ResourceAction("73d5636237ffec432c61a75fe9335015")(rwh)
      status(result) must equal(NOT_MODIFIED)
    }
  }
}
