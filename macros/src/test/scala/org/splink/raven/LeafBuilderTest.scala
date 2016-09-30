package org.splink.raven

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import org.splink.raven.Exceptions.NoFallbackException
import play.api.mvc.{Action, Results}
import play.api.test.FakeRequest

import scala.concurrent.Future

class LeafBuilderTest extends FlatSpec with Matchers with ScalaFutures {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val request = FakeRequest()

  def successAction = Action(Results.Ok("action"))

  case class TestException(msg: String) extends RuntimeException

  def failedAction = Action { r =>
    throw TestException("sync fail")
  }

  def failedAsyncAction = Action.async(Future {
    throw TestException("async fail")
  })

  val builder = new LeafBuilderImpl with LeafToolsImpl with SerializerImpl {}

  val requestId = RequestId("123")

  def build[T](info: FunctionInfo[T], isRoot: Boolean): Future[PageletResult] =
    builder.leafBuilderService.build(
      Leaf('one, info),
      Seq.empty, requestId, isRoot)

  def buildWithFallback[T, U](info: FunctionInfo[T], fallback: FunctionInfo[U],isRoot: Boolean): Future[PageletResult] =
    builder.leafBuilderService.build(
      Leaf('one, info).withFallback(fallback),
      Seq.empty, requestId, isRoot)

  "LeafBuilder#build (as root without fallback)" should "yield a BrickResult" in {
    build(FunctionInfo(successAction _), isRoot = true).futureValue should equal(PageletResult("action"))
  }

  it should "yield a NoFallbackException if an Exception is thrown" in {
    build(FunctionInfo(failedAction _), isRoot = true).failed.futureValue should equal(NoFallbackException('one))
  }

  it should "yield a NoFallbackException if an Exception is thrown within the Future" in {
    build(FunctionInfo(failedAsyncAction _), isRoot = true).failed.futureValue should equal(NoFallbackException('one))
  }



  "LeafBuilder#build (not as root without fallback)" should "yield a BrickResult" in {
    build(FunctionInfo(successAction _), isRoot = false).futureValue should equal(PageletResult("action"))
  }

  it should "yield an empty BrickResult if an Exception is thrown" in {
    build(FunctionInfo(failedAction _), isRoot = false).futureValue should equal(PageletResult.empty)
  }

  it should "yield an empty BrickResult if an Exception is thrown within the Future" in {
    build(FunctionInfo(failedAsyncAction _), isRoot = false).futureValue should equal(PageletResult.empty)
  }



  "LeafBuilder#build (as root with successful fallback)" should "yield a BrickResult" in {
    val result = buildWithFallback(FunctionInfo(successAction _), FunctionInfo(successAction _), isRoot = true)
    result.futureValue should equal(PageletResult("action"))
  }

  it should "yield a BrickResult if an Exception is thrown in the main Action" in {
    val result = buildWithFallback(FunctionInfo(failedAction _), FunctionInfo(successAction _), isRoot = true)
    result.futureValue should equal(PageletResult("action"))
  }

  it should "yield a BrickResult if an Exception is thrown in the main Action Future" in {
    val result = buildWithFallback(FunctionInfo(failedAsyncAction _), FunctionInfo(successAction _), isRoot = true)
    result.futureValue should equal(PageletResult("action"))
  }



  "LeafBuilder#build (not as root with successful fallback)" should "yield a BrickResult" in {
    val result = buildWithFallback(FunctionInfo(successAction _), FunctionInfo(successAction _), isRoot = false)
    result.futureValue should equal(PageletResult("action"))
  }

  it should "yield a BrickResult if an Exception is thrown in the main Action" in {
    val result = buildWithFallback(FunctionInfo(failedAction _), FunctionInfo(successAction _), isRoot = false)
    result.futureValue should equal(PageletResult("action"))
  }

  it should "yield a BrickResult if an Exception is thrown in the main Action Future" in {
    val result = buildWithFallback(FunctionInfo(failedAsyncAction _), FunctionInfo(successAction _), isRoot = false)
    result.futureValue should equal(PageletResult("action"))
  }



  "LeafBuilder#build (as root with failing fallback)" should "yield a BrickResult if an Exception is thrown in the main Action" in {
    val result = buildWithFallback(FunctionInfo(failedAction _), FunctionInfo(failedAction _), isRoot = true)
    result.failed.futureValue shouldBe an [TestException]
  }

  "LeafBuilder#build (not as root with failing fallback)" should "yield a BrickResult if an Exception is thrown in the main Action" in {
    val result = buildWithFallback(FunctionInfo(failedAction _), FunctionInfo(failedAction _), isRoot = false)
    result.futureValue should equal(PageletResult.empty)
  }



  "LeafBuilder#build (as root with failing fallback)" should "yield a BrickResult if an Exception is thrown in the main Action Future" in {
    val result = buildWithFallback(FunctionInfo(failedAsyncAction _), FunctionInfo(failedAsyncAction _), isRoot = true)
    result.failed.futureValue shouldBe an [TestException]
  }

  "LeafBuilder#build (not as root with failing fallback)" should "yield a BrickResult if an Exception is thrown in the main Action Future" in {
    val result = buildWithFallback(FunctionInfo(failedAsyncAction _), FunctionInfo(failedAsyncAction _), isRoot = false)
    result.futureValue should equal(PageletResult.empty)
  }


}
