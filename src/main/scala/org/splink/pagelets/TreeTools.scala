package org.splink.pagelets

import play.api.mvc.{BaseController, Results}

import scala.language.implicitConversions

trait TreeTools {
  implicit def treeOps(tree: Tree): TreeOps

  trait TreeOps {
    def skip(id: PageletId): Tree
    def replace(id: PageletId, other: Pagelet): Tree
    def find(id: PageletId): Option[Pagelet]
  }
}

trait TreeToolsImpl extends TreeTools { self: BaseController =>
  override implicit def treeOps(tree: Tree): TreeOps = new TreeOpsImpl(tree)

  class TreeOpsImpl(tree: Tree) extends TreeOps {
    val log = play.api.Logger("TreeTools")

    override def find(id: PageletId): Option[Pagelet] = {
      def rec(p: Pagelet): Option[Pagelet] = p match {
        case _ if p.id == id => Some(p)
        case Tree(_, children_, _) => children_.flatMap(rec).headOption
        case _ => None
      }
      rec(tree)
    }

    override def skip(id: PageletId) = {
      def f = Action(Results.Ok)
      log.debug(s"skip $id")
      replace(id, Leaf(id, FunctionInfo(() => f, Nil)))
    }

    override def replace(id: PageletId, other: Pagelet): Tree = {
      def rec(p: Pagelet): Pagelet = p match {
        case b@Tree(_, childs, _) if childs.exists(_.id == id) =>
          val idx = childs.indexWhere(_.id == id)
          b.copy(children = childs.updated(idx, other))

        case b@Tree(_, childs, _) =>
          b.copy(children = childs.map(rec))

        case any =>
          any
      }

      if (id == tree.id) {
        other match {
          case t: Tree => t
          case l: Leaf[_, _] =>
            log.debug(s"replace with a new Tree $id")
            Tree(id, Seq(l), tree.combine)
        }
      } else {
        log.debug(s"replace $id")
        rec(tree).asInstanceOf[Tree]
      }
    }
  }
}
