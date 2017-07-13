package org.splink.pagelets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import helpers.FutureHelper
import org.scalatest.{FlatSpec, Matchers}
import play.api.mvc._
import play.api.test.{FakeRequest, StubControllerComponentsFactory}

import scala.concurrent.Future
import scala.language.implicitConversions

class LeafBuilderTest extends FlatSpec with Matchers with FutureHelper with StubControllerComponentsFactory {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val request = FakeRequest()

  val Action = stubControllerComponents().actionBuilder

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

  val builder = new LeafBuilderImpl with BaseController with ActionBuilderImpl {
    override def controllerComponents: ControllerComponents = stubControllerComponents()
  }

  val requestId = RequestId("RequestId")

  def build[T](info: FunctionInfo[T], isMandatory: Boolean): PageletResult =
    builder.leafBuilderService.build(Leaf('one, info, isMandatory = isMandatory), Seq.empty, requestId)

  def buildWithFallback[T, U](info: FunctionInfo[T], fallback: FunctionInfo[U], isMandatory: Boolean): PageletResult =
    builder.leafBuilderService.build(Leaf('one, info, isMandatory = isMandatory).withFallback(fallback), Seq.empty, requestId)

  /**
    * Without fallback
    */

  "LeafBuilder#build (mandatory without fallback)" should "yield the body of the result" in {
    val result = build(FunctionInfo(successAction _), isMandatory = true)
    result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
    result.body.consume should equal("action")
  }

  it should "yield an empty body if an Action fails" in {
    val result = build(FunctionInfo(failedAction _), isMandatory = true)
    result.mandatoryFailedPagelets.map(_.futureValue).head should be(true)
    result.body.consume should equal("")
  }

    it should "yield an empty body if an async Action fails" in {
      val result = build(FunctionInfo(failedAsyncAction _), isMandatory = true)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(true)
      result.body.consume should equal("")
    }


    "LeafBuilder#build (not mandatory without fallback)" should "yield the body of the result" in {
      val result = build(FunctionInfo(successAction _), isMandatory = false)
      result.body.consume should equal("action")
    }

    it should "yield an empty body if an Action fails" in {
      val result = build(FunctionInfo(failedAction _), isMandatory = false)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("")
    }

    it should "yield an empty body if an async Action fails" in {
      val result = build(FunctionInfo(failedAsyncAction _), isMandatory = false)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("")
    }

    /**
      * With fallback
      */

    // Not root node: Successful fallback

    "LeafBuilder#build (mandatory with successful fallback)" should "yield the body of the result" in {
      val result = buildWithFallback(FunctionInfo(successAction _), FunctionInfo(failedAction _), isMandatory = true)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("action")
    }

    it should "yield the fallback if an Action fails" in {
      val result = buildWithFallback(FunctionInfo(failedAction _), FunctionInfo(successAction _), isMandatory = true)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("action")
    }

    it should "yield the fallback if an async Action fails" in {
      val result = buildWithFallback(FunctionInfo(failedAsyncAction _), FunctionInfo(successAction _), isMandatory = true)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("action")
    }

    // mandatory node: Successful fallback

    "LeafBuilder#build (not mandatory with successful fallback)" should "yield the body of the result" in {
      val result = buildWithFallback(FunctionInfo(successAction _), FunctionInfo(failedAction _), isMandatory = false)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("action")
    }

    it should "yield the the fallback, if an Action fails" in {
      val result = buildWithFallback(FunctionInfo(failedAction _), FunctionInfo(successAction _), isMandatory = false)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("action")
    }

    it should "yield yield the fallback, if an async Action fails" in {
      val result = buildWithFallback(FunctionInfo(failedAsyncAction _), FunctionInfo(successAction _), isMandatory = false)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("action")
    }

    // Sync: default and fallback fail

    "LeafBuilder#build (mandatory with failing fallback)" should "yield an empty body if the default and fallback Actions fail" in {
      val result = buildWithFallback(FunctionInfo(failedAction _), FunctionInfo(failedAction _), isMandatory = true)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(true)
      result.body.consume should equal("")
    }

    "LeafBuilder#build (not mandatory with failing fallback)" should "yield an empty body if the default and fallback Actions fail" in {
      val result = buildWithFallback(FunctionInfo(failedAction _), FunctionInfo(failedAction), isMandatory = false)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("")
    }

    // Async: default and fallback fail

    "LeafBuilder#build (mandatory with failing fallback)" should "yield an empty body if the default and fallback async Actions fail" in {
      val result = buildWithFallback(FunctionInfo(failedAsyncAction _), FunctionInfo(failedAsyncAction _), isMandatory = true)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(true)
      result.body.consume should equal("")
    }

    "LeafBuilder#build (not mandatory with failing fallback)" should "yield an empty body if the default and fallback async Actions fail" in {
      val result = buildWithFallback(FunctionInfo(failedAsyncAction _), FunctionInfo(failedAsyncAction _), isMandatory = false)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("")
    }

}
