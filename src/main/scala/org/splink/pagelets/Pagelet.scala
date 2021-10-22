package org.splink.pagelets

import akka.stream.scaladsl.{Concat, Source}

case class Arg(name: String, value: Any)


sealed trait Pagelet {
  def id: PageletId
}

case class Leaf[A, B] private(id: PageletId, info: FunctionInfo[A],
                              fallback: Option[FunctionInfo[B]] = None,
                              css: Seq[Css] = Seq.empty,
                              javascript: Seq[Javascript] = Seq.empty,
                              javascriptTop: Seq[Javascript] = Seq.empty,
                              metaTags: Seq[MetaTag] = Seq.empty,
                              isMandatory: Boolean = false) extends Pagelet {

  def withFallback(fallback: FunctionInfo[B]) = copy(fallback = Some(fallback))
  def withJavascript(js: Javascript*) = copy(javascript = Seq(js:_*))
  def withJavascriptTop(js: Javascript*) = copy(javascriptTop = Seq(js:_*))
  def withCss(css: Css*) = copy(css = Seq(css:_*))
  def withMetaTags(tags: MetaTag*) = copy(metaTags = Seq(tags:_*))
  def setMandatory(value: Boolean) = copy(isMandatory = value)
  override def toString = s"Leaf(${id.name})"
}

object Tree {
  def combine(results: Seq[PageletResult]): PageletResult =
    results.foldLeft(PageletResult.empty) { (acc, next) =>
      PageletResult(
        Source.combine(acc.body, next.body)(Concat.apply),//TODO offer a choice of merge strategy
        acc.js ++ next.js,
        acc.jsTop ++ next.jsTop,
        acc.css ++ next.css,
        acc.cookies ++ next.cookies,
        (acc.metaTags ++ next.metaTags).distinct,
        acc.mandatoryFailedPagelets ++ next.mandatoryFailedPagelets)
    }
}

case class Tree private(id: PageletId, children: Seq[Pagelet],
                        combine: Seq[PageletResult] => PageletResult = Tree.combine) extends Pagelet {

  override def equals(that: Any): Boolean =
    that match {
      case that: Tree => this.hashCode == that.hashCode
      case _ => false
    }

  override def hashCode: Int = 31 * (31 + id.hashCode) + children.hashCode

  override def toString = s"Tree(${id.name}\n ${children.map(_.toString)})"
}
