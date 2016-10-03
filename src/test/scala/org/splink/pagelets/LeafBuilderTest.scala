package org.splink.pagelets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import org.splink.pagelets.Exceptions.NoFallbackException
import play.api.mvc.{Action, Results}
import play.api.test.FakeRequest

import scala.concurrent.Future
import scala.language.implicitConversions

class LeafBuilderTest extends FlatSpec with Matchers with ScalaFutures with MockitoSugar {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val request = FakeRequest()

  val successAction = Action(Results.Ok("action"))

  case class TestException(msg: String) extends RuntimeException

  val failedAction = Action { r =>
    throw TestException("sync fail")
  }

  val failedAsyncAction = Action.async(Future {
    throw TestException("async fail")
  })

  val builder = new LeafBuilderImpl with LeafTools with Serializer {
    override val serializer: SerializerService = mock[SerializerService]

    val leafOpsMock = mock[LeafOps]

    override implicit def leafOps(leaf: Leaf[_, _]): LeafOps = leafOpsMock

    when(leafOpsMock.execute(FunctionInfo(successAction), Seq.empty)).thenReturn(Future.successful(PageletResult("action")))
    when(leafOpsMock.execute(FunctionInfo(failedAction), Seq.empty)).thenReturn(Future.failed(TestException("sync fail")))
    when(leafOpsMock.execute(FunctionInfo(failedAsyncAction), Seq.empty)).thenReturn(Future.failed(TestException("async fail")))

  }

  val requestId = RequestId("123")

  def build[T](info: FunctionInfo[T], isRoot: Boolean): Future[PageletResult] =
    builder.leafBuilderService.build(
      Leaf('one, info),
      Seq.empty, requestId, isRoot)

  def buildWithFallback[T, U](info: FunctionInfo[T], fallback: FunctionInfo[U], isRoot: Boolean): Future[PageletResult] =
    builder.leafBuilderService.build(
      Leaf('one, info).withFallback(fallback),
      Seq.empty, requestId, isRoot)

  "LeafBuilder#build (as root without fallback)" should "yield a PageletResult" in {
    build(FunctionInfo(successAction), isRoot = true).futureValue should equal(PageletResult("action"))
  }

  it should "yield a NoFallbackException if an Exception is thrown" in {
    build(FunctionInfo(failedAction), isRoot = true).failed.futureValue should equal(NoFallbackException('one))
  }

  it should "yield a NoFallbackException if an Exception is thrown within the Future" in {
    build(FunctionInfo(failedAsyncAction), isRoot = true).failed.futureValue should equal(NoFallbackException('one))
  }



  "LeafBuilder#build (not as root without fallback)" should "yield a PageletResult" in {
    build(FunctionInfo(successAction), isRoot = false).futureValue should equal(PageletResult("action"))
  }

  it should "yield an empty PageletResult if an Exception is thrown" in {
    build(FunctionInfo(failedAction), isRoot = false).futureValue should equal(PageletResult.empty)
  }

  it should "yield an empty PageletResult if an Exception is thrown within the Future" in {
    build(FunctionInfo(failedAsyncAction), isRoot = false).futureValue should equal(PageletResult.empty)
  }



  "LeafBuilder#build (as root with successful fallback)" should "yield a PageletResult" in {
    val result = buildWithFallback(FunctionInfo(successAction), FunctionInfo(successAction), isRoot = true)
    result.futureValue should equal(PageletResult("action"))
  }

  it should "yield a PageletResult if an Exception is thrown in the main Action" in {
    val result = buildWithFallback(FunctionInfo(failedAction), FunctionInfo(successAction), isRoot = true)
    result.futureValue should equal(PageletResult("action"))
  }

  it should "yield a PageletResult if an Exception is thrown in the main Action Future" in {
    val result = buildWithFallback(FunctionInfo(failedAsyncAction), FunctionInfo(successAction), isRoot = true)
    result.futureValue should equal(PageletResult("action"))
  }



  "LeafBuilder#build (not as root with successful fallback)" should "yield a PageletResult" in {
    val result = buildWithFallback(FunctionInfo(successAction), FunctionInfo(successAction), isRoot = false)
    result.futureValue should equal(PageletResult("action"))
  }

  it should "yield a PageletResult if an Exception is thrown in the main Action" in {
    val result = buildWithFallback(FunctionInfo(failedAction), FunctionInfo(successAction), isRoot = false)
    result.futureValue should equal(PageletResult("action"))
  }

  it should "yield a PageletResult if an Exception is thrown in the main Action Future" in {
    val result = buildWithFallback(FunctionInfo(failedAsyncAction), FunctionInfo(successAction), isRoot = false)
    result.futureValue should equal(PageletResult("action"))
  }



  "LeafBuilder#build (as root with failing fallback)" should "yield a PageletResult if an Exception is thrown in the main Action" in {
    val result = buildWithFallback(FunctionInfo(failedAction), FunctionInfo(failedAction), isRoot = true)
    result.failed.futureValue shouldBe an[TestException]
  }

  "LeafBuilder#build (not as root with failing fallback)" should "yield a PageletResult if an Exception is thrown in the main Action" in {
    val result = buildWithFallback(FunctionInfo(failedAction), FunctionInfo(failedAction), isRoot = false)
    result.futureValue should equal(PageletResult.empty)
  }



  "LeafBuilder#build (as root with failing fallback)" should "yield a PageletResult if an Exception is thrown in the main Action Future" in {
    val result = buildWithFallback(FunctionInfo(failedAsyncAction), FunctionInfo(failedAsyncAction), isRoot = true)
    result.failed.futureValue shouldBe an[TestException]
  }

  "LeafBuilder#build (not as root with failing fallback)" should "yield a PageletResult if an Exception is thrown in the main Action Future" in {
    val result = buildWithFallback(FunctionInfo(failedAsyncAction), FunctionInfo(failedAsyncAction), isRoot = false)
    result.futureValue should equal(PageletResult.empty)
  }


}
