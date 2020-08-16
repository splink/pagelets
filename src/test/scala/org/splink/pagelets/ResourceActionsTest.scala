package org.splink.pagelets

import org.scalamock.scalatest.MockFactory
import org.scalatestplus.play._
import play.api.Environment
import play.api.http.HeaderNames
import play.api.mvc.{BaseController, ControllerComponents}
import play.api.test.{FakeRequest, StubControllerComponentsFactory}
import play.api.test.Helpers._

import scala.language.reflectiveCalls

class ResourceActionsTest extends PlaySpec with MockFactory with StubControllerComponentsFactory {
  implicit val env = Environment.simple()

  def actions = new ResourceActionsImpl with Resources with BaseController {
    override def controllerComponents: ControllerComponents = stubControllerComponents()
    override val resources: ResourceProvider = mock[ResourceProvider]
  }

  val print = Fingerprint("hash")
  val request = FakeRequest()

  "ResourceAction" should {
    "return the resource with status Ok for a known fingerprint" in {
      val a = actions

      (a.resources.contentFor _).expects(print).returning {
        Some(ResourceContent("""console.log("a");""", JsMimeType))
      }

      val result = a.ResourceAction(print.toString)(request)

      status(result) must equal(OK)
      contentType(result) must equal(Some(JsMimeType.name))
      contentAsString(result) must equal("""console.log("a");""")
    }

    "return BadRequest if the fingerprint is unknown" in {
      val a = actions
      (a.resources.contentFor _).expects(*).returning(None)

      val result = a.ResourceAction("something")(request)
      status(result) must equal(BAD_REQUEST)
    }

    "return headers with etag" in {
      val a = actions
      (a.resources.contentFor _).expects(print).returning {
        Some(ResourceContent("""console.log("a");""", JsMimeType))
      }

      val result = a.ResourceAction(print.toString)(request)

      header(HeaderNames.ETAG, result) must equal(Some(s""""$print""""))
    }

    "return NotModified if the server holds a resource for the fingerprint in the etag (IF_NONE_MATCH) header" in {
      val a = actions
      (a.resources.contains _).expects(print).returning(true)

      val rwh = request.withHeaders(HeaderNames.IF_NONE_MATCH -> print.toString)
      val result = a.ResourceAction(print.toString)(rwh)
      status(result) must equal(NOT_MODIFIED)
    }
  }
}
