package org.splink.pagelets

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import helpers.FutureHelper
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec
import play.api.mvc._
import play.api.test.{FakeRequest, StubControllerComponentsFactory}

import scala.concurrent.Future
import scala.language.implicitConversions

class LeafBuilderTest extends AnyFlatSpec with Matchers with FutureHelper with StubControllerComponentsFactory {
  implicit val system = ActorSystem()
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
    body = Source.future(Future.successful(ByteString(body))),
    results = Seq(Future((None, None, Seq.empty)))
  )

  val builder = new LeafBuilderImpl with BaseController with PageletActionBuilderImpl {
    override def controllerComponents: ControllerComponents = stubControllerComponents()
  }

  val requestId = RequestId("RequestId")

  def build[T](info: FunctionInfo[T], isMandatory: Boolean): PageletResult =
    builder.leafBuilderService.build(Leaf(PageletId("one"), info, isMandatory = isMandatory), Seq.empty, requestId)

  def buildWithFallback[T, U](info: FunctionInfo[T], fallback: FunctionInfo[U], isMandatory: Boolean): PageletResult =
    builder.leafBuilderService.build(Leaf(PageletId("one"), info, isMandatory = isMandatory).withFallback(fallback), Seq.empty, requestId)

  /**
    * Without fallback
    */

  "LeafBuilder#build (mandatory without fallback)" should "yield the body of the result" in {
    val result = build(FunctionInfo(() => successAction), isMandatory = true)
    result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
    result.body.consume should equal("action")
  }

  it should "yield an empty body if an Action fails" in {
    val result = build(FunctionInfo(() => failedAction), isMandatory = true)
    result.mandatoryFailedPagelets.map(_.futureValue).head should be(true)
    result.body.consume should equal("")
  }

    it should "yield an empty body if an async Action fails" in {
      val result = build(FunctionInfo(() => failedAsyncAction), isMandatory = true)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(true)
      result.body.consume should equal("")
    }


    "LeafBuilder#build (not mandatory without fallback)" should "yield the body of the result" in {
      val result = build(FunctionInfo(() => successAction), isMandatory = false)
      result.body.consume should equal("action")
    }

    it should "yield an empty body if an Action fails" in {
      val result = build(FunctionInfo(() => failedAction), isMandatory = false)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("")
    }

    it should "yield an empty body if an async Action fails" in {
      val result = build(FunctionInfo(() => failedAsyncAction), isMandatory = false)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("")
    }

    /**
      * With fallback
      */

    // Not root node: Successful fallback

    "LeafBuilder#build (mandatory with successful fallback)" should "yield the body of the result" in {
      val result = buildWithFallback(FunctionInfo(() => successAction), FunctionInfo(() => failedAction), isMandatory = true)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("action")
    }

    it should "yield the fallback if an Action fails" in {
      val result = buildWithFallback(FunctionInfo(() => failedAction), FunctionInfo(() => successAction), isMandatory = true)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("action")
    }

    it should "yield the fallback if an async Action fails" in {
      val result = buildWithFallback(FunctionInfo(() => failedAsyncAction), FunctionInfo(() => successAction), isMandatory = true)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("action")
    }

    // mandatory node: Successful fallback

    "LeafBuilder#build (not mandatory with successful fallback)" should "yield the body of the result" in {
      val result = buildWithFallback(FunctionInfo(() => successAction), FunctionInfo(() => failedAction), isMandatory = false)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("action")
    }

    it should "yield the the fallback, if an Action fails" in {
      val result = buildWithFallback(FunctionInfo(() => failedAction), FunctionInfo(() => successAction), isMandatory = false)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("action")
    }

    it should "yield yield the fallback, if an async Action fails" in {
      val result = buildWithFallback(FunctionInfo(() => failedAsyncAction), FunctionInfo(() => successAction), isMandatory = false)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("action")
    }

    // Sync: default and fallback fail

    "LeafBuilder#build (mandatory with failing fallback)" should "yield an empty body if the default and fallback Actions fail" in {
      val result = buildWithFallback(FunctionInfo(() => failedAction), FunctionInfo(() => failedAction), isMandatory = true)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(true)
      result.body.consume should equal("")
    }

    "LeafBuilder#build (not mandatory with failing fallback)" should "yield an empty body if the default and fallback Actions fail" in {
      val result = buildWithFallback(FunctionInfo(() => failedAction), FunctionInfo(() => failedAction), isMandatory = false)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("")
    }

    // Async: default and fallback fail

    "LeafBuilder#build (mandatory with failing fallback)" should "yield an empty body if the default and fallback async Actions fail" in {
      val result = buildWithFallback(FunctionInfo(() => failedAsyncAction), FunctionInfo(() => failedAsyncAction), isMandatory = true)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(true)
      result.body.consume should equal("")
    }

    "LeafBuilder#build (not mandatory with failing fallback)" should "yield an empty body if the default and fallback async Actions fail" in {
      val result = buildWithFallback(FunctionInfo(() => failedAsyncAction), FunctionInfo(() => failedAsyncAction), isMandatory = false)
      result.mandatoryFailedPagelets.map(_.futureValue).head should be(false)
      result.body.consume should equal("")
    }

}
