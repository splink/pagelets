package org.splink.pagelets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import helpers.FutureHelper
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import play.api.mvc.{Action, Results}
import play.api.test.FakeRequest

import scala.concurrent.Future
import scala.language.implicitConversions

class LeafBuilderTest extends FlatSpec with Matchers with MockitoSugar with FutureHelper {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val request = FakeRequest()

  val successAction = Action(Results.Ok("action"))

  case class TestException(msg: String) extends RuntimeException

  val failedAction = Action { _ =>
    throw TestException("sync fail")
  }

  val failedAsyncAction = Action.async(Future {
    throw TestException("async fail")
  })

  def mkResult(body: String) = PageletResult(
    body = Source.fromFuture(Future.successful(ByteString(body))),
    cookies = Seq(Future(Seq.empty)))

  val builder = new LeafBuilderImpl with ActionBuilder {
    val leafServiceMock = mock[ActionService]

    override implicit def actionService: ActionService = leafServiceMock

    when(leafServiceMock.execute(any[Symbol], any[FunctionInfo[_]], any[Seq[Arg]])).thenReturn(Right(Action(Results.Ok)))

    when(leafServiceMock.execute('one, FunctionInfo(successAction), Seq.empty)).thenReturn(Right(successAction))
    when(leafServiceMock.execute('one, FunctionInfo(failedAction), Seq.empty)).thenReturn(Right(failedAction))
    when(leafServiceMock.execute('one, FunctionInfo(failedAsyncAction), Seq.empty)).thenReturn(Right(failedAsyncAction))
  }

  val requestId = RequestId("123")

  def build[T](info: FunctionInfo[T], isRoot: Boolean): PageletResult =
    builder.leafBuilderService.build(
      Leaf('one, info),
      Seq.empty, requestId, isRoot)

  def buildWithFallback[T, U](info: FunctionInfo[T], fallback: FunctionInfo[U], isRoot: Boolean): PageletResult =
    builder.leafBuilderService.build(
      Leaf('one, info).withFallback(fallback),
      Seq.empty, requestId, isRoot)

  /**
    * Without fallback
    */

  "LeafBuilder#build (as root without fallback)" should "yield the body of the result" in {
    build(FunctionInfo(successAction), isRoot = true).body.consume should equal("action")
  }

  it should "yield an empty body if an Action fails" in {
    build(FunctionInfo(failedAction), isRoot = true).body.consume should equal("")
  }

  it should "yield an empty body if an async Action fails" in {
    build(FunctionInfo(failedAsyncAction), isRoot = true).body.consume should equal("")
  }


  "LeafBuilder#build (not as root without fallback)" should "yield the body of the result" in {
    build(FunctionInfo(successAction), isRoot = false).body.consume should equal("action")
  }

  it should "yield an empty body if an Action fails" in {
    build(FunctionInfo(failedAction), isRoot = false).body.consume should equal("")
  }

  it should "yield an empty body if an async Action fails" in {
    build(FunctionInfo(failedAsyncAction), isRoot = false).body.consume should equal("")
  }

  /**
    * With fallback
    */

  // Not root node: Successful fallback

  "LeafBuilder#build (as root with successful fallback)" should "yield the body of the result" in {
    val result = buildWithFallback(FunctionInfo(successAction), FunctionInfo(failedAction), isRoot = true)
    result.body.consume should equal("action")
  }

  it should "yield the fallback if an Action fails" in {
    val result = buildWithFallback(FunctionInfo(failedAction), FunctionInfo(successAction), isRoot = true)
    result.body.consume should equal("action")
  }

  it should "yield the fallback if an async Action fails" in {
    val result = buildWithFallback(FunctionInfo(failedAsyncAction), FunctionInfo(successAction), isRoot = true)
    result.body.consume should equal("action")
  }

  // As root node: Successful fallback

  "LeafBuilder#build (not as root with successful fallback)" should "yield the body of the result" in {
    val result = buildWithFallback(FunctionInfo(successAction), FunctionInfo(failedAction), isRoot = false)
    result.body.consume should equal("action")
  }

  it should "yield the the fallback, if an Action fails" in {
    val result = buildWithFallback(FunctionInfo(failedAction), FunctionInfo(successAction), isRoot = false)
    result.body.consume should equal("action")
  }

  it should "yield yield the fallback, if an async Action fails" in {
    val result = buildWithFallback(FunctionInfo(failedAsyncAction), FunctionInfo(successAction), isRoot = false)
    result.body.consume should equal("action")
  }

  // Sync: default and fallback fail

  "LeafBuilder#build (as root with failing fallback)" should "yield an empty body if the default and fallback Actions fail" in {
    val result = buildWithFallback(FunctionInfo(failedAction), FunctionInfo(failedAction), isRoot = true)
    result.body.consume should equal("")
  }

  "LeafBuilder#build (not as root with failing fallback)" should "yield an empty body if the default and fallback Actions fail" in {
    val result = buildWithFallback(FunctionInfo(failedAction), FunctionInfo(failedAction), isRoot = false)
    result.body.consume should equal("")
  }

  // Async: default and fallback fail

  "LeafBuilder#build (as root with failing fallback)" should "yield an empty body if the default and fallback async Actions fail" in {
    val result = buildWithFallback(FunctionInfo(failedAsyncAction), FunctionInfo(failedAsyncAction), isRoot = true)
    result.body.consume should equal("")
  }

  "LeafBuilder#build (not as root with failing fallback)" should "yield an empty body if the default and fallback async Actions fail" in {
    val result = buildWithFallback(FunctionInfo(failedAsyncAction), FunctionInfo(failedAsyncAction), isRoot = false)
    result.body.consume should equal("")
  }
}
