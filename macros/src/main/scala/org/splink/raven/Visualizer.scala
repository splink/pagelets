package org.splink.raven


trait Visualizer {
  def visualizer: VisualizerService

  trait VisualizerService {
    def visualize(p: Part): String
  }

}

trait VisualizerImpl extends Visualizer {
  override val visualizer = new VisualizerService {
    override def visualize(p: Part) = {
      def rec(p: Part, layer: Int = 0): String = p match {
        case t: Tree =>
          val a = space(layer) + t.id + "\n"
          a + t.children.map(c => rec(c, layer + 1)).mkString
        case Leaf(id, fnc, _) =>
          space(layer) + id + mkArgsString(fnc) + "\n"
      }

      rec(p)
    }

    def space(layer: Int) = (0 to layer).map(_ => "-").mkString

    def mkArgsString(fnc: FunctionInfo[_]) =
      if (fnc.types.isEmpty) ""
      else "(" + fnc.types.map { case (name, typ) =>
        val index = typ.lastIndexOf(".")
        name + ": " + (if (index > -1) typ.substring(index + 1) else typ)
      }.mkString(", ") + ")"
  }
}
