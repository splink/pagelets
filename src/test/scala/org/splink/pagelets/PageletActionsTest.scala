package org.splink.pagelets

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalamock.scalatest.MockFactory
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Environment
import play.api.mvc._
import play.api.test.{FakeRequest, StubControllerComponentsFactory}
import play.api.test.Helpers._
import play.twirl.api.Html

import scala.concurrent.Future
import scala.language.{implicitConversions, reflectiveCalls}

class PageletActionsTest extends PlaySpec with GuiceOneAppPerSuite with MockFactory with StubControllerComponentsFactory {
  implicit val system = ActorSystem()
  implicit val env = Environment.simple()
  implicit val request = FakeRequest()

  def actions = new PageletActionsImpl with BaseController with PageBuilder with TreeTools with Resources {

    override def controllerComponents: ControllerComponents = stubControllerComponents()

    override val builder: PageBuilderService = mock[PageBuilderService]

    val opsMock = mock[TreeOps]
    override implicit def treeOps(tree: Tree): TreeOps = opsMock

    override val resources: ResourceProvider = mock[ResourceProvider]
    (resources.update(_: Seq[Resource])(_: Environment)).expects(*, *).
      returning(Some(Fingerprint("print"))).
      anyNumberOfTimes()
  }

  def leaf = Leaf(PageletId("id"), null)
  def tree(r: RequestHeader) = Tree(PageletId("id"), Seq.empty)
  def title(r: RequestHeader) = "Title"

  def mkResult(body: String) = PageletResult(Source.single(ByteString(body)))

  def buildMock(service: PageBuilder#PageBuilderService)(result: PageletResult) =
    (service.build(_: Pagelet, _: Arg)(_: Request[AnyContent])).expects(*, *, *).returning(result).anyNumberOfTimes()

  val onError = Call("get", "error")

  "PageletAction" should {
    "return a Pagelet if the tree contains the pagelet for the given id" in {
      val a = actions
      (a.opsMock.find _).expects(PageletId("one")).returning(Some(leaf))

      buildMock(a.builder)(mkResult("body"))

      val action = a.PageletAction.async(onError)(tree, PageletId("one")) { (_, page) =>
        Html(s"${page.body}")
      }

      val result = action(request)

      status(result) must equal(OK)
      contentAsString(result) must equal("body")
    }

    "return NotFound if the tree does not contain a pagelet for the given id" in {
      val a = actions
      (a.opsMock.find _).expects(PageletId("one")).returning(None)
      buildMock(a.builder)(mkResult("body"))

      val action = a.PageletAction.async(onError)(tree, PageletId("one")) { (_, page) =>
        Html(s"${page.body}")
      }

      val result = action(request)
      status(result) must equal(NOT_FOUND)
      contentAsString(result) must include("one")
      contentAsString(result) must include("does not exist")
    }

    "redirect if a pagelet declared as mandatory fails" in {
      val a = actions
      (a.opsMock.find _).expects(PageletId("one")).returning(Some(leaf))
      buildMock(a.builder)(mkResult("").copy(mandatoryFailedPagelets = Seq(Future.successful(true))))

      val action = a.PageletAction.async(onError)(tree, PageletId("one")) { (_, page) =>
        Html(s"${page.body}")
      }

      val result = action(request)
      status(result) must equal(TEMPORARY_REDIRECT)
    }

  }

  "PageAction#async" should {
    "return a Page" in {
      val a = actions
      buildMock(a.builder)(mkResult("body"))

      val action = a.PageAction.async(onError)(title, tree) { (request, page) =>
        Html(s"${page.body}${title(request)}")
      }

      val result = action(request)

      status(result) must equal(OK)
      contentAsString(result) must equal("bodyTitle")
    }

    "invoke the error template if a pagelet declared as mandatory fails" in {
      val a = actions
      buildMock(a.builder)(mkResult("").copy(mandatoryFailedPagelets = Seq(Future.successful(true))))

      val action = a.PageAction.async(onError)(title, tree) { (request, page) =>
        Html(s"${page.body}${title(request)}")
      }

      val result = action(request)
      status(result) must equal(TEMPORARY_REDIRECT)
    }

    "return the corresponding headers alongside the Page" in {
      val a = actions
      buildMock(a.builder)(
        mkResult("body").copy(
          results = Seq(
            Future.successful((None, None, Seq(Cookie("name", "value")))),
            Future.successful((None, None, Seq(Cookie("name1", "value"))))
          )))

      val action = a.PageAction.async(onError)(title, tree) { (request, page) =>
        Html(s"${page.body}${title(request)}")
      }

      val result = action(request)

      status(result) must equal(OK)
      contentAsString(result) must equal("bodyTitle")
      cookies(result) must contain theSameElementsAs(Cookies(Seq(Cookie("name", "value"), Cookie("name1", "value"))))
    }

  }

  "PageAction#stream" should {
    "return a Page" in {
      val a = actions
      buildMock(a.builder)(mkResult("body"))

      val action = a.PageAction.stream(title, tree) { (request, page) =>
        page.body.map(b => Html(b.utf8String + title(request)))
      }

      val result = action(request)
      status(result) must equal(OK)
      contentAsString(result) must equal("bodyTitle")
    }

    // when the page is streamed, it's too late to redirect
    "return a Page if a pagelet declared as mandatory fails" in {
      val a = actions
      buildMock(a.builder)(mkResult("body").copy(mandatoryFailedPagelets = Seq(Future.successful(true))))

      val action = a.PageAction.stream(title, tree) { (request, page) =>
        page.body.map(b => Html(b.utf8String + title(request)))
      }

      val result = action(request)
      status(result) must equal(OK)
      contentAsString(result) must equal("bodyTitle")
    }

    "return a Page with Cookies" in {
      val a = actions
      buildMock(a.builder)(
        mkResult("body").copy(
          results = Seq(Future.successful((None, None, Seq(Cookie("name", "value")))))))

      val action = a.PageAction.stream(title, tree) { (request, page) =>
        page.body.map(b => Html(b.utf8String + title(request)))
      }

      val result = action(request)
      status(result) must equal(OK)
      contentAsString(result) must include("bodyTitle")
      contentAsString(result) must include("setCookie('name', 'value'")
    }
  }
}
