package org.splink.pagelets

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import helpers.FutureHelper
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec
import play.api.mvc._
import play.api.test.{FakeRequest, StubControllerComponentsFactory}


class PageBuilderTest extends AnyFlatSpec with Matchers with FutureHelper  with StubControllerComponentsFactory {

  import FunctionMacros._

  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val request = FakeRequest()

  val Action = stubControllerComponents().actionBuilder

  def action(s: String) = () => Action(Results.Ok(s))

  val tree = Tree(Symbol("root"), Seq(
    Leaf(Symbol("one"), action("one")),
    Tree(Symbol("two"), Seq(
      Leaf(Symbol("three"), action("three")),
      Leaf(Symbol("four"), action("four"))
    ))
  ))

  def mkResult(body: String) = PageletResult(Source.single(ByteString(body)))

  val builder = new PageBuilderImpl with LeafBuilder {
    override val leafBuilderService = new LeafBuilderService {
      override def build(leaf: Leaf[_, _], args: Seq[Arg], requestId: RequestId)(implicit r: Request[AnyContent]) =
        mkResult(leaf.id.name)
    }
  }.builder

  def opsify(t: Tree) = new TreeToolsImpl with BaseController {
      override def controllerComponents: ControllerComponents = stubControllerComponents()
  }.treeOps(t)

  "PageBuilder#builder" should "build a complete tree" in {
    builder.build(tree).body.consume should equal("onethreefour")
  }

  it should "build a subtree" in {
    builder.build(opsify(tree).find(Symbol("two")).get).body.consume should equal("threefour")
  }

  it should "build a leaf" in {
    builder.build(opsify(tree).find(Symbol("four")).get).body.consume should equal("four")
  }
}
