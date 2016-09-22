package org.splink.raven

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import play.api.mvc.{Results, Action, AnyContent}
import play.api.test.FakeRequest
import scala.concurrent.ExecutionContext.Implicits.global

class TreeToolsTest extends FlatSpec with Matchers with ScalaFutures {

  import FunctionMacros._

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  val action = () => Action(Results.Ok("action"))

  def treeOps = opsify(Tree(
    'root, Seq(
      Leaf('child1, action),
      Leaf('child2, action),
      Tree('child3, Seq(
        Leaf('child31, action)
      ))
    )
  ))

  def opsify(t: Tree) = new TreeToolsImpl {}.treeOps(t)

  "TreeTools#find" should "find a Leaf in the tree" in {
    treeOps.find('child31) should equal(
      Some(
          Leaf('child31, action)
      )
    )
  }

  it should "find a Tree in the tree" in {
    treeOps.find('child3) should equal(
      Some(
        Tree('child3, Seq(
          Leaf('child31, action)
        ))
      )
    )
  }

  it should "find the root of the tree" in {
    treeOps.find('root) should equal(
      Some(
        Tree(
          'root, Seq(
            Leaf('child1, action),
            Leaf('child2, action),
            Tree('child3, Seq(
              Leaf('child31, action)
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

    val newTree = treeOps.skip('child3)
    val body = actionFor(newTree)('child3).map(bodyOf)

    body should equal(Some(""))
  }

  "TreeTools#replace" should "replace the part with the given id with another Tree" in {
    val newTree = treeOps.replace('child3, Tree('new, Seq(
      Leaf('newChild1, action),
      Leaf('newChild2, action)
    )))

    opsify(newTree).find('new) should equal(
      Some(
        Tree('new, Seq(
          Leaf('newChild1, action),
          Leaf('newChild2, action)
        ))
      )
    )
  }

  it should "replace the root with a different Tree" in {
    val newTree = treeOps.replace('root, Tree('new, Seq(
      Leaf('newChild1, action),
      Leaf('newChild2, action)
    )))

    opsify(newTree).find('root) shouldBe None

    opsify(newTree).find('new) should equal(
      Some(
        Tree('new, Seq(
          Leaf('newChild1, action),
          Leaf('newChild2, action)
        ))
      )
    )
  }

  it should "return a Tree with one Leaf when asked to replace the root with a Leaf" in {
    // root must be a Tree, only then one can chain TreeTools function like tree.replace(...).skip(..).replace(
    val fnc = () => "someFunction"
    val newTree = treeOps.replace('root, Leaf('new, FunctionInfo(fnc, Nil)))

    opsify(newTree).find('new) should equal(
      Some(
        Leaf('new, FunctionInfo(fnc, Nil)
        ))
    )

    opsify(newTree).find('root) should equal(
      Some(
        Tree('root, Seq(
          Leaf('new, FunctionInfo(fnc, Nil))
        ))
      )
    )
  }
}
