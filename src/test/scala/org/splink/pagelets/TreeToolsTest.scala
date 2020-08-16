package org.splink.pagelets

import akka.actor.ActorSystem
import helpers.FutureHelper
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec
import play.api.mvc._
import play.api.test.{FakeRequest, StubControllerComponentsFactory}

class TreeToolsTest extends AnyFlatSpec with Matchers with FutureHelper with StubControllerComponentsFactory {

  import FunctionMacros._

  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher

  val Action = stubControllerComponents().actionBuilder

  val action = () => Action(Results.Ok("action"))

  def treeOps = opsify(Tree(
    Symbol("root"), Seq(
      Leaf(Symbol("child1"), action),
      Leaf(Symbol("child2"), action),
      Tree(Symbol("child3"), Seq(
        Leaf(Symbol("child31"), action)
      ))
    )
  ))

  def opsify(t: Tree) = new TreeToolsImpl with BaseController {
    override def controllerComponents: ControllerComponents = stubControllerComponents()
  }.treeOps(t)

  "TreeTools#find" should "find a Leaf in the tree" in {
    treeOps.find(Symbol("child31")) should equal(
      Some(
          Leaf(Symbol("child31"), action)
      )
    )
  }

  it should "find a Tree in the tree" in {
    treeOps.find(Symbol("child3")) should equal(
      Some(
        Tree(Symbol("child3"), Seq(
          Leaf(Symbol("child31"), action)
        ))
      )
    )
  }

  it should "find the root of the tree" in {
    treeOps.find(Symbol("root")) should equal(
      Some(
        Tree(
          Symbol("root"), Seq(
            Leaf(Symbol("child1"), action),
            Leaf(Symbol("child2"), action),
            Tree(Symbol("child3"), Seq(
              Leaf(Symbol("child31"), action)
            ))
          )
        )
      )
    )
  }

  "TreeTools#skip" should "replace the part with the given id with a Leaf with contains a call to an empty Action" in {
    def bodyOf(action: Action[AnyContent]) = {
      val result = action(FakeRequest()).futureValue
      val body = result.body.consumeData.map(_.utf8String).futureValue
      body
    }

    def actionFor(t: Tree)(id: Symbol) = opsify(t).find(id).map { part =>
      part.asInstanceOf[Leaf[_, _]].info.fnc.asInstanceOf[() => Action[AnyContent]]()
    }

    val newTree = treeOps.skip(Symbol("child3"))
    val body = actionFor(newTree)(Symbol("child3")).map(bodyOf)

    body should equal(Some(""))
  }

  "TreeTools#replace" should "replace the part with the given id with another Tree" in {
    val newTree = treeOps.replace(Symbol("child3"), Tree(Symbol("new"), Seq(
      Leaf(Symbol("newChild1"), action),
      Leaf(Symbol("newChild2"), action)
    )))

    opsify(newTree).find(Symbol("new")) should equal(
      Some(
        Tree(Symbol("new"), Seq(
          Leaf(Symbol("newChild1"), action),
          Leaf(Symbol("newChild2"), action)
        ))
      )
    )
  }

  it should "replace the root with a different Tree" in {
    val newTree = treeOps.replace(Symbol("root"), Tree(Symbol("new"), Seq(
      Leaf(Symbol("newChild1"), action),
      Leaf(Symbol("newChild2"), action)
    )))

    opsify(newTree).find(Symbol("root")) shouldBe None

    opsify(newTree).find(Symbol("new")) should equal(
      Some(
        Tree(Symbol("new"), Seq(
          Leaf(Symbol("newChild1"), action),
          Leaf(Symbol("newChild2"), action)
        ))
      )
    )
  }

  it should "return a Tree with one Leaf when asked to replace the root with a Leaf" in {
    // root must be a Tree, only then one can chain TreeTools function like tree.replace(...).skip(..).replace(
    val fnc = () => "someFunction"
    val newTree = treeOps.replace(Symbol("root"), Leaf(Symbol("new"), FunctionInfo(fnc, Nil)))

    opsify(newTree).find(Symbol("new")) should equal(
      Some(
        Leaf(Symbol("new"), FunctionInfo(fnc, Nil)
        ))
    )

    opsify(newTree).find(Symbol("root")) should equal(
      Some(
        Tree(Symbol("root"), Seq(
          Leaf(Symbol("new"), FunctionInfo(fnc, Nil))
        ))
      )
    )
  }
}
