package org.splink.pagelets

case class Arg(name: String, value: Any)

sealed trait Pagelet {
  def id: Symbol
}

case class Leaf[A, B](id: Symbol,
                      private[pagelets] val info: FunctionInfo[A],
                      private[pagelets] val fallback: Option[FunctionInfo[B]] = None) extends Pagelet {
  def withFallback(fallback: FunctionInfo[B]) = copy(fallback = Some(fallback))

  override def toString = s"Leaf(${id.name})"
}

object Tree {
  def combine(results: Seq[PageletResult]): PageletResult =
    results.foldLeft(PageletResult.empty) { (acc, next) =>
      PageletResult(
        acc.body + next.body,
        acc.js ++ next.js,
        acc.jsTop ++ next.jsTop,
        acc.css ++ next.css,
        acc.cookies ++ next.cookies,
        acc.metaTags ++ next.metaTags)
    }
}

case class Tree private(id: Symbol, children: Seq[Pagelet], combine: Seq[PageletResult] => PageletResult = Tree.combine) extends Pagelet {

  override def equals(that: Any): Boolean =
    that match {
      case that: Tree => this.hashCode == that.hashCode
      case _ => false
    }

  override def hashCode: Int = 31 * (31 + id.hashCode) + children.hashCode

  override def toString = s"Tree(${id.name}\n ${children.map(_.toString)})"
}