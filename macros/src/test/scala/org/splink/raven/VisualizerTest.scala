package org.splink.raven

import org.scalatest.{Matchers, FlatSpec}
import play.api.mvc.{Action, Results}

class VisualizerTest extends FlatSpec with Matchers {

  import FunctionMacros._

  def action(s: String) = () => Action(Results.Ok(s))
  def action2(s: String, i: Int) = Action(Results.Ok(s + i))

  val tree = Tree('root, Seq(
    Leaf('one, action("one")),
    Tree('two, Seq(
      Leaf('three, action2 _),
      Leaf('four, action("four"))
    ))
  ))

  val visualizer = new VisualizerImpl {}.visualizer

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
