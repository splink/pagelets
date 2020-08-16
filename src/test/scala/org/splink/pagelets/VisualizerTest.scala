package org.splink.pagelets

import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec
import play.api.mvc.Results
import play.api.test.StubControllerComponentsFactory

class VisualizerTest extends AnyFlatSpec with Matchers with StubControllerComponentsFactory {

  import FunctionMacros._

  val Action = stubControllerComponents().actionBuilder

  def action(s: String) = () => Action(Results.Ok(s))
  def action2(s: String, i: Int) = Action(Results.Ok(s + i))

  val tree = Tree(Symbol("root"), Seq(
    Leaf(Symbol("one"), action("one")),
    Tree(Symbol("two"), Seq(
      Leaf(Symbol("three"), action2 _),
      Leaf(Symbol("four"), action("four"))
    ))
  ))

  val visualizer = new VisualizerImpl {}

  "Visualizer#visualize" should "visualize a tree" in {
    visualizer.visualize(tree) should equal(
      """root
        |-one
        |-two
        |--three(s:String, i:Int)
        |--four
        |""".stripMargin)
  }

}
