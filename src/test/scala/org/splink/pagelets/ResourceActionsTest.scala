package org.splink.pagelets

import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play._
import play.api.Environment
import play.api.http.HeaderNames
import play.api.test.FakeRequest
import play.api.test.Helpers._

import org.mockito.Matchers._
import org.mockito.Mockito._

class ResourceActionsTest extends PlaySpec with MockitoSugar {
  implicit val env = Environment.simple()

  def actions = new ResourceActionsImpl with Resources {
    override val resources: ResourceProvider = mock[ResourceProvider]
  }

  val print = Fingerprint("hash")
  val request = FakeRequest()

  "ResourceAction" should {
    "return the resource with status Ok for a known fingerprint" in {
      val a = actions
      when(a.resources.contains(print)).thenReturn(true)
      when(a.resources.contentFor(print)).thenReturn {
        Some(ResourceContent("""console.log("a");""", JsMimeType))
      }

      val result = a.ResourceAction(print.toString)(request)

      status(result) must equal(OK)
      contentType(result) must equal(Some(JsMimeType.name))
      contentAsString(result) must equal("""console.log("a");""")
    }

    "return BadRequest if the fingerprint is unknown" in {
      val a = actions
      when(a.resources.contains(any[Fingerprint])).thenReturn(false)
      when(a.resources.contentFor(any[Fingerprint])).thenReturn(None)

      val result = a.ResourceAction("something")(request)
      status(result) must equal(BAD_REQUEST)
    }

    "return headers with etag" in {
      val a = actions
      when(a.resources.contains(print)).thenReturn(true)
      when(a.resources.contentFor(print)).thenReturn {
        Some(ResourceContent("""console.log("a");""", JsMimeType))
      }

      val result = a.ResourceAction(print.toString)(request)

      header(HeaderNames.ETAG, result) must equal(Some(print.toString))
    }

    "return NotModified if the server holds a resource for the fingerprint in the etag (IF_NONE_MATCH) header" in {
      val a = actions
      when(a.resources.contains(print)).thenReturn(true)

      val rwh = request.withHeaders(HeaderNames.IF_NONE_MATCH -> print.toString)
      val result = a.ResourceAction(print.toString)(rwh)
      status(result) must equal(NOT_MODIFIED)
    }
  }
}
