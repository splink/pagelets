package org.splink.pagelets

import helpers.FutureHelper
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import play.api.http.HttpEntity
import play.api.mvc.{Action, Controller, ResponseHeader, Result}
import play.api.test.FakeRequest

import org.mockito.Matchers._
import org.mockito.Mockito._

class ResultToolsTest extends FlatSpec with Matchers with FutureHelper with MockitoSugar {

  val tools = new ResultToolsImpl with Serializer {
    override val serializer: SerializerService = mock[SerializerService]

    when(serializer.serialize[MetaTag](any[MetaTag])).thenReturn {
      Right[SerializationError, String]("serialized")
    }
  }

  import tools._

  val result = Result(ResponseHeader(200), HttpEntity.NoEntity)

  "ResultTools#withJavaScript" should "add one Javascript to the result headers" in {
    val newResult = result.withJavascript(Javascript("some.js"))

    newResult.header.headers should contain(Javascript.name -> "some.js")
  }

  it should "add multiple Javascript to the result headers" in {
    val newResult = result.withJavascript(Javascript("some.js"), Javascript("some-more.js"))

    newResult.header.headers should contain(Javascript.name -> "some.js,some-more.js")
  }

  "ResultTools#withJavaScriptTop" should "add one Javascript to the result headers" in {
    val newResult = result.withJavascriptTop(Javascript("some.js"))

    newResult.header.headers should contain(Javascript.nameTop -> "some.js")
  }

  it should "add multiple Javascript to the result headers" in {
    val newResult = result.withJavascriptTop(Javascript("some.js"), Javascript("some-more.js"))

    newResult.header.headers should contain(Javascript.nameTop -> "some.js,some-more.js")
  }

  "ResultTools#withCss" should "add one Css to the result headers" in {
    val newResult = result.withCss(Css("some.css"))

    newResult.header.headers should contain(Css.name -> "some.css")
  }

  it should "add multiple Css to the result headers" in {
    val newResult = result.withCss(Css("some.css"), Css("some-more.css"))

    newResult.header.headers should contain(Css.name -> "some.css,some-more.css")
  }

  "ResultTools#withMetaTags" should "add one MetaTag to the result headers" in {
    val newResult = result.withMetaTags(MetaTag("some", "tag"))

    newResult.header.headers.get(MetaTag.name).get should equal("serialized")
  }

  it should "add multiple MetaTag to the result headers" in {
    val newResult = result.withMetaTags(MetaTag("any", "tag"), MetaTag("another", "tag"))

    newResult.header.headers.get(MetaTag.name).get should contain(',')
  }

  "ResultToolsImpl provides an implicit Writable which" should "permit an Action to return PageletResult as Result" in {
    class TestController extends Controller {
      def index = Action {
        Ok(PageletResult("Body"))
      }
    }

    val result = new TestController().index.apply(FakeRequest()).futureValue
    result.header.status should equal(200)
  }

}
