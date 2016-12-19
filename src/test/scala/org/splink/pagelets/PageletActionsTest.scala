package org.splink.pagelets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.Environment
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html

import scala.concurrent.{ExecutionContext, Future}
import scala.language.{implicitConversions, reflectiveCalls}

class PageletActionsTest extends PlaySpec with OneAppPerSuite with MockitoSugar {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val env = Environment.simple()
  implicit val request = FakeRequest()

  def actions = new PageletActionsImpl with Controller with PageBuilder with TreeTools with Resources {

    override val builder: PageBuilderService = mock[PageBuilderService]

    val opsMock = mock[TreeOps]
    override implicit def treeOps(tree: Tree): TreeOps = opsMock

    override val resources: ResourceProvider = mock[ResourceProvider]
  }

  def leaf = mock[Leaf[_,_]]
  def tree(r: RequestHeader) = mock[Tree]

  def mkResult(body: String) = PageletResult(Source.single(ByteString(body)))

  def buildMock(service: PageBuilder#PageBuilderService)(result: PageletResult) =
    when(service.build(
      any[Leaf[_, _]],
      anyVararg[Arg])(
      any[ExecutionContext],
      any[Request[AnyContent]])).thenReturn(result)

  "PageletAction" should {
    "return a Pagelet if the tree contains the pagelet for the given id" in {
      val a = actions
      when(a.opsMock.find('one)).thenReturn(Some(leaf))
      buildMock(a.builder)(mkResult("body"))

      val action = a.PageletAction.async(e => Html(s"${e.title}"))(tree, 'one) { (_, page) =>
        Html(s"${page.body}")
      }

      val result = action(request)

      status(result) must equal(OK)
      contentAsString(result) must equal("body")
    }

    "return NotFound if the tree does not contain a pagelet for the given id" in {
      val a = actions
      when(a.opsMock.find('one)).thenReturn(None)
      buildMock(a.builder)(mkResult("body"))

      val action = a.PageletAction.async(e => Html(s"${e.title}"))(tree, 'one) { (_, page) =>
        Html(s"${page.body}")
      }

      val result = action(request)
      status(result) must equal(NOT_FOUND)
      contentAsString(result) must equal("'one does not exist")
    }

    "invoke the error template if a pagelet declared as mandatory fails" in {
      val a = actions
      when(a.opsMock.find('one)).thenReturn(Some(leaf))
      buildMock(a.builder)(mkResult("").copy(mandatoryFailedPagelets = Seq(Future.successful(true))))

      def errorTemplate(e: ErrorPage)(implicit r: RequestHeader) = {
        Html(s"${e.title} uri: ${request.uri}")
      }

      val action = a.PageletAction.async(errorTemplate)(tree, 'one) { (_, page) =>
        Html(s"${page.body}")
      }

      val result = action(request)
      status(result) must equal(INTERNAL_SERVER_ERROR)
      contentAsString(result) must equal("one uri: /")
    }

  }

  "PageAction#async" should {
    "return a Page" in {
      val a = actions
      buildMock(a.builder)(mkResult("body"))

      val action = a.PageAction.async(e => Html(s"${e.title}"))("title", tree) { (_, page) =>
        Html(s"${page.body}")
      }

      val result = action(request)

      status(result) must equal(OK)
      contentAsString(result) must equal("body")
    }

    "invoke the error template if a pagelet declared as mandatory fails" in {
      val a = actions
      buildMock(a.builder)(mkResult("").copy(mandatoryFailedPagelets = Seq(Future.successful(true))))

      def errorTemplate(e: ErrorPage)(implicit r: RequestHeader) = Html(s"${e.title} uri: ${r.uri}")

      val action = a.PageAction.async(errorTemplate)("title", tree) { (_, page) =>
        Html(s"${page.body}")
      }

      val result = action(request)
      status(result) must equal(INTERNAL_SERVER_ERROR)
      contentAsString(result) must equal("title uri: /")
    }

  }

  "PageAction#stream" should {
    "return a Page" in {
      val a = actions
      buildMock(a.builder)(mkResult("body"))

      val action = a.PageAction.stream("title", tree) { (_, page) =>
        page.body.map(b => Html(b.utf8String))
      }

      val result = action(request)
      status(result) must equal(OK)
      contentAsString(result) must equal("body")
    }

    // when the page is streamed, it's too late to set the status code header to InternalServerError
    "return a Page if a pagelet declared as mandatory fails" in {
      val a = actions
      buildMock(a.builder)(mkResult("body").copy(mandatoryFailedPagelets = Seq(Future.successful(true))))

      val action = a.PageAction.stream("title", tree) { (_, page) =>
        page.body.map(b => Html(b.utf8String))
      }

      val result = action(request)
      status(result) must equal(OK)
      contentAsString(result) must equal("body")
    }

    "return a Page with Cookies" in {
      val a = actions
      buildMock(a.builder)(
        mkResult("body").copy(
          cookies = Seq(Future.successful(Seq(Cookie("name", "value"))))))

      val action = a.PageAction.stream("title", tree) { (_, page) =>
        page.body.map(b => Html(b.utf8String))
      }

      val result = action(request)
      status(result) must equal(OK)
      contentAsString(result) must include("body")
      contentAsString(result) must include("setCookie('name', 'value'")
    }
  }
}
