package org.splink.raven

import play.api.mvc.{Action, Results}

trait TreeTools {
  implicit def treeOps(tree: Tree): TreeOps

  trait TreeOps {
    def skip(id: Symbol): Tree
    def replace(id: Symbol, other: Part): Tree
    def find(id: Symbol): Option[Part]
  }

}

trait TreeToolsImpl extends TreeTools {
  override implicit def treeOps(tree: Tree): TreeOps = new TreeOpsImpl(tree)

  class TreeOpsImpl(tree: Tree) extends TreeOps {

    override def skip(id: Symbol) = {
      def f = Action(Results.Ok)
      replace(id, Leaf(id, FunctionInfo(f _, Nil)))
    }

    override def replace(id: Symbol, other: Part): Tree = {
      def rec(p: Part): Part = p match {
        case b@Tree(_, childs, _) if childs.exists(_.id == id) =>
          val idx = childs.indexWhere(_.id == id)
          b.copy(children = childs.updated(idx, other))

        case b@Tree(_, childs, _) =>
          b.copy(children = childs.map(rec))

        case pagelet =>
          pagelet
      }

      if (id == tree.id) {
        other match {
          case t: Tree => t
          case l: Leaf[_, _] => Tree(id, Seq(l), tree.combine)
        }
      } else {
        rec(tree).asInstanceOf[Tree]
      }
    }

    override def find(id: Symbol): Option[Part] = {
      def rec(p: Part): Option[Part] = p match {
        case _ if p.id == id => Some(p)
        case Tree(_, children_, _) => children_.flatMap(rec).headOption
        case _ => None
      }
      rec(tree)
    }
  }
}
