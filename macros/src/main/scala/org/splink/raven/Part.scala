package org.splink.raven

case class Arg(name: String, value: Any)

sealed trait Part {
  def id: Symbol
}

case class Leaf[A, B](id: Symbol,
                      private[raven] val info: FunctionInfo[A],
                      private[raven] val fallback: Option[FunctionInfo[B]] = None) extends Part {
  def withFallback(fallback: FunctionInfo[B]) = copy(fallback = Some(fallback))

  override def toString = s"Leaf(${id.name})"
}

object Tree {

  def apply(id: Symbol, children: Seq[Part], combiner: Seq[BrickResult] => BrickResult = Tree.combine): Tree =
    new Tree(id, children) {
      override def combine: Seq[BrickResult] => BrickResult = combiner
    }

  def combine(results: Seq[BrickResult]): BrickResult =
    results.foldLeft(BrickResult.empty) { (acc, next) =>
      BrickResult(
        acc.body + next.body,
        acc.js ++ next.js,
        acc.jsTop ++ next.jsTop,
        acc.css ++ next.css,
        acc.cookies ++ next.cookies,
        acc.metaTags ++ next.metaTags)
    }
}

case class Tree private(id: Symbol, children: Seq[Part]) extends Part {
  def combine: Seq[BrickResult] => BrickResult = Tree.combine

  override def toString = s"Tree(${id.name}\n ${children.map(_.toString)})"
}