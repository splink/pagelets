package org.splink.raven

import scala.language.implicitConversions
import play.api.mvc.{Action, Results}

trait TreeTools {
  implicit def treeOps(tree: Tree): TreeOps

  trait TreeOps {
    def skip(id: Symbol): Tree
    def replace(id: Symbol, other: Pagelet): Tree
    def find(id: Symbol): Option[Pagelet]
  }

}

trait TreeToolsImpl extends TreeTools {
  override implicit def treeOps(tree: Tree): TreeOps = new TreeOpsImpl(tree)

  class TreeOpsImpl(tree: Tree) extends TreeOps {

    override def find(id: Symbol): Option[Pagelet] = {
      def rec(p: Pagelet): Option[Pagelet] = p match {
        case _ if p.id == id => Some(p)
        case Tree(_, children_) => children_.flatMap(rec).headOption
        case _ => None
      }
      rec(tree)
    }

    override def skip(id: Symbol) = {
      def f = Action(Results.Ok)
      replace(id, Leaf(id, FunctionInfo(f _, Nil)))
    }

    override def replace(id: Symbol, other: Pagelet): Tree = {
      def rec(p: Pagelet): Pagelet = p match {
        case b@Tree(_, childs) if childs.exists(_.id == id) =>
          val idx = childs.indexWhere(_.id == id)
          b.copy(children = childs.updated(idx, other))

        case b@Tree(_, childs) =>
          b.copy(children = childs.map(rec))

        case any =>
          any
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
  }
}
