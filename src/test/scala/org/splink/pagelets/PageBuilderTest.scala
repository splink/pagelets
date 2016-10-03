package org.splink.pagelets

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import play.api.mvc.{Action, AnyContent, Request, Results}
import play.api.test.FakeRequest

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class PageBuilderTest extends FlatSpec with Matchers with ScalaFutures {

  import FunctionMacros._

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val request = FakeRequest()

  def action(s: String) = () => Action(Results.Ok(s))

  val tree = Tree('root, Seq(
    Leaf('one, action("one")),
    Tree('two, Seq(
      Leaf('three, action("three")),
      Leaf('four, action("four"))
    ))
  ))

  def delay[T](d: FiniteDuration)(f: Future[T]) =
    akka.pattern.after(d, using = system.scheduler)(f)


  val builder = new PageBuilderImpl with LeafBuilder {
    override val leafBuilderService = new LeafBuilderService {
      override def build(leaf: Leaf[_, _], args: Seq[Arg], requestId: RequestId, isRoot: Boolean)(
        implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer) =
        delay((Math.random() * 5).toInt.millis)(Future(PageletResult(leaf.id.name)))
    }
  }.builder

  def opsify(t: Tree) = new TreeToolsImpl {}.treeOps(t)

  "PageBuilder#builder" should "build a complete tree" in {
    builder.build(tree).futureValue.body should equal("onethreefour")
  }

  it should "build a subtree" in {
    builder.build(opsify(tree).find('two).get).futureValue.body should equal("threefour")
  }

  it should "build a leaf" in {
    builder.build(opsify(tree).find('four).get).futureValue.body should equal("four")
  }
}
