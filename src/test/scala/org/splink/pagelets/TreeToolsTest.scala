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
    PageletId("root"), Seq(
      Leaf(PageletId("child1"), action),
      Leaf(PageletId("child2"), action),
      Tree(PageletId("child3"), Seq(
        Leaf(PageletId("child31"), action)
      ))
    )
  ))

  def opsify(t: Tree) = new TreeToolsImpl with BaseController {
    override def controllerComponents: ControllerComponents = stubControllerComponents()
  }.treeOps(t)

  "TreeTools#find" should "find a Leaf in the tree" in {
    treeOps.find(PageletId("child31")) should equal(
      Some(
          Leaf(PageletId("child31"), action)
      )
    )
  }

  it should "find a Tree in the tree" in {
    treeOps.find(PageletId("child3")) should equal(
      Some(
        Tree(PageletId("child3"), Seq(
          Leaf(PageletId("child31"), action)
        ))
      )
    )
  }

  it should "find the root of the tree" in {
    treeOps.find(PageletId("root")) should equal(
      Some(
        Tree(
          PageletId("root"), Seq(
            Leaf(PageletId("child1"), action),
            Leaf(PageletId("child2"), action),
            Tree(PageletId("child3"), Seq(
              Leaf(PageletId("child31"), action)
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

    def actionFor(t: Tree)(id: PageletId) = opsify(t).find(id).map { part =>
      part.asInstanceOf[Leaf[_, _]].info.fnc.asInstanceOf[() => Action[AnyContent]]()
    }

    val newTree = treeOps.skip(PageletId("child3"))
    val body = actionFor(newTree)(PageletId("child3")).map(bodyOf)

    body should equal(Some(""))
  }

  "TreeTools#replace" should "replace the part with the given id with another Tree" in {
    val newTree = treeOps.replace(PageletId("child3"), Tree(PageletId("new"), Seq(
      Leaf(PageletId("newChild1"), action),
      Leaf(PageletId("newChild2"), action)
    )))

    opsify(newTree).find(PageletId("new")) should equal(
      Some(
        Tree(PageletId("new"), Seq(
          Leaf(PageletId("newChild1"), action),
          Leaf(PageletId("newChild2"), action)
        ))
      )
    )
  }

  it should "replace the root with a different Tree" in {
    val newTree = treeOps.replace(PageletId("root"), Tree(PageletId("new"), Seq(
      Leaf(PageletId("newChild1"), action),
      Leaf(PageletId("newChild2"), action)
    )))

    opsify(newTree).find(PageletId("root")) shouldBe None

    opsify(newTree).find(PageletId("new")) should equal(
      Some(
        Tree(PageletId("new"), Seq(
          Leaf(PageletId("newChild1"), action),
          Leaf(PageletId("newChild2"), action)
        ))
      )
    )
  }

  it should "return a Tree with one Leaf when asked to replace the root with a Leaf" in {
    // root must be a Tree, only then one can chain TreeTools function like tree.replace(...).skip(..).replace(
    val fnc = () => "someFunction"
    val newTree = treeOps.replace(PageletId("root"), Leaf(PageletId("new"), FunctionInfo(fnc, Nil)))

    opsify(newTree).find(PageletId("new")) should equal(
      Some(
        Leaf(PageletId("new"), FunctionInfo(fnc, Nil)
        ))
    )

    opsify(newTree).find(PageletId("root")) should equal(
      Some(
        Tree(PageletId("root"), Seq(
          Leaf(PageletId("new"), FunctionInfo(fnc, Nil))
        ))
      )
    )
  }

  "TreeTools#filter" should "filter all pagelets and their children for the given ids" in {
    treeOps.filter(_.id != PageletId("child3")) should equal(
      Tree(PageletId("root"), Seq(
        Leaf(PageletId("child1"), action),
        Leaf(PageletId("child2"), action)
      ))
    )
  }

 it should "filter a single pagelet leaf for the given id" in {
    treeOps.filter(_.id != PageletId("child2")) should equal(
      Tree(PageletId("root"), Seq(
        Leaf(PageletId("child1"), action),
        Tree(PageletId("child3"), Seq(
          Leaf(PageletId("child31"), action)
        ))
      ))
    )
  }

  it should "not filter the root node" in {
    treeOps.filter(_.id != PageletId("root")) should equal(
      Tree(PageletId("root"), Seq(
        Leaf(PageletId("child1"), action),
        Leaf(PageletId("child2"), action),
        Tree(PageletId("child3"), Seq(
          Leaf(PageletId("child31"), action)
        ))
      ))
    )
  }

  it should "filter multiple nodes" in {
    treeOps.filter(!_.id.name.startsWith("child")) should equal(
      Tree(PageletId("root"), Seq.empty)
    )
  }

}
