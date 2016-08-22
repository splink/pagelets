package org.splink.raven

trait PageletId

case class Arg(name: String, value: Any)

sealed trait Pagelet {
  def id: PageletId
}

case class Leaf[A, B](id: PageletId,
                      private[raven] val info: FunctionInfo[A],
                      private[raven] val fallback: Option[FunctionInfo[B]] = None) extends Pagelet {
  def withFallback(fallback: FunctionInfo[B]) = copy(fallback = Some(fallback))
}

object Tree {
  def combine(results: Seq[BrickResult]): BrickResult = results.foldLeft(BrickResult.empty) { (acc, next) =>
    BrickResult(acc.body + next.body, acc.js ++ next.js, acc.css ++ next.css, acc.cookies ++ next.cookies)
  }
}

case class Tree(id: PageletId, children: Seq[Pagelet], combine: Seq[BrickResult] => BrickResult = Tree.combine) extends Pagelet