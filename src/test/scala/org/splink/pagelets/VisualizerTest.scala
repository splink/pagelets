package org.splink.pagelets

import org.scalatest.{FlatSpec, Matchers}
import play.api.mvc.{Action, Results}
import play.api.test.StubControllerComponentsFactory

class VisualizerTest extends FlatSpec with Matchers with StubControllerComponentsFactory {

  import FunctionMacros._

  val Action = stubControllerComponents().actionBuilder

  def action(s: String) = () => Action(Results.Ok(s))
  def action2(s: String, i: Int) = Action(Results.Ok(s + i))

  val tree = Tree('root, Seq(
    Leaf('one, action("one")),
    Tree('two, Seq(
      Leaf('three, action2 _),
      Leaf('four, action("four"))
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
