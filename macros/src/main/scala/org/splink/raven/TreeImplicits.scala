package org.splink.raven

import play.api.mvc.{Action, Results}

object TreeImplicits {

  implicit class TreeOps(tree: Tree) {

    def skip(id: PageletId) = {
      def f = Action(Results.Ok)
      replace(id, Leaf(id, FunctionInfo(f _, Nil)))
    }

    def replace(id: PageletId, other: Pagelet): Tree = {
      def rec(p: Pagelet): Pagelet = p match {
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

    def find(id: String): Option[Pagelet] = {
      def rec(p: Pagelet): Option[Pagelet] = p match {
        case _ if p.id.toString == id => Some(p)
        case Tree(_, children_, _) => children_.flatMap(rec).headOption
        case _ => None
      }
      rec(tree)
    }
  }

}
