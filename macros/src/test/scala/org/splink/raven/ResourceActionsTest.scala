package org.splink.raven

import org.scalatest.BeforeAndAfter
import org.scalatestplus.play._
import org.splink.raven.Resources.JsMimeType
import play.api.Environment
import play.api.http.HeaderNames
import play.api.test.FakeRequest
import play.api.test.Helpers._

class ResourceActionsTest extends PlaySpec with BeforeAndAfter {
  val actions = new ResourceActionsImpl {}

  implicit val env = Environment.simple()
  val request = FakeRequest()

  before {
    Resources().clear()
  }

  "ResourceAction" should {
    "return the resource with status Ok for a known fingerprint" in {
      val print = Resources().update(Set(Javascript("a.js")))
      val result = actions.ResourceAction(print.get.toString)(request)

      status(result) must equal(OK)
      contentType(result) must equal(Some(JsMimeType.name))
      contentAsString(result) must equal(
        """console.log("a");
          |""".stripMargin)
    }

    "return BadRequest if the fingerprint is unknown" in {
      val result = actions.ResourceAction("something")(request)
      status(result) must equal(BAD_REQUEST)
    }

    "return headers with etag" in {
      val print = Resources().update(Set(Javascript("a.js")))
      val result = actions.ResourceAction(print.get.toString)(request)

      header(HeaderNames.ETAG, result) must equal(Some(print.get.toString))
    }

    "return NotModified if the server holds a resource for the fingerprint in the etag (IF_NONE_MATCH) header" in {
      val print = Resources().update(Set(Javascript("a.js")))

      val rwh = request.withHeaders(HeaderNames.IF_NONE_MATCH -> print.get.toString)
      val result = actions.ResourceAction(print.get.toString)(rwh)
      status(result) must equal(NOT_MODIFIED)
    }
  }
}
