package org.splink.raven

case class Arg(name: String, value: Any)

sealed trait Part {
  def id: Symbol
}

case class Leaf[A, B](id: Symbol,
                      private[raven] val info: FunctionInfo[A],
                      private[raven] val fallback: Option[FunctionInfo[B]] = None) extends Part {
  def withFallback(fallback: FunctionInfo[B]) = copy(fallback = Some(fallback))
}

object Tree {
  def combine(results: Seq[BrickResult]): BrickResult = results.foldLeft(BrickResult.empty) { (acc, next) =>
    BrickResult(
      acc.body + next.body,
      acc.js ++ next.js,
      acc.css ++ next.css,
      acc.cookies ++ next.cookies,
      acc.metaTags ++ next.metaTags)
  }
}

case class Tree(id: Symbol, children: Seq[Part], combine: Seq[BrickResult] => BrickResult = Tree.combine) extends Part