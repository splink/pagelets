package org.splink.pagelets

trait Visualizer {
  def visualize(p: Pagelet): String
}

trait VisualizerImpl extends Visualizer {
  override def visualize(p: Pagelet) = {
    def rec(p: Pagelet, layer: Int = 0): String = p match {
      case t: Tree =>
        val a = space(layer) + t.id.name + "\n"
        a + t.children.map(c => rec(c, layer + 1)).mkString
      case Leaf(id, fnc, _) =>
        space(layer) + id.name + mkArgsString(fnc) + "\n"
    }

    rec(p)
  }

  def space(layer: Int) = (1 to layer).map(_ => "-").mkString

  def mkArgsString(fnc: FunctionInfo[_]) =
    if (fnc.types.isEmpty) ""
    else "(" + fnc.types.map { case (name, typ) =>
      val index = typ.lastIndexOf(".")
      name + ":" + (if (index > -1) typ.substring(index + 1) else typ)
    }.mkString(", ") + ")"
}
