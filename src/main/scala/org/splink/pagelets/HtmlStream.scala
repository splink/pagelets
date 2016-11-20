package org.splink.pagelets

import akka.stream.scaladsl.{Concat, Source}
import play.twirl.api.{Appendable, Format, Html, HtmlFormat}

class HtmlStream(val source: Source[Html, _]) extends Appendable[HtmlStream] {
  def andThen(other: HtmlStream): HtmlStream =
    HtmlStream(Source.combine(source, other.source)(Concat.apply))
}

case object HtmlStream {
  def apply(source: Source[Html, _]) = new HtmlStream(source)
}

object HtmlStreamFormat extends Format[HtmlStream] {
  def raw(text: String): HtmlStream =
    new HtmlStream(Source.single(Html(text)))

  def escape(text: String): HtmlStream =
    raw(HtmlFormat.escape(text).body)

  def empty: HtmlStream = raw("")

  def fill(elements: scala.collection.immutable.Seq[HtmlStream]): HtmlStream =
    if(elements.isEmpty) HtmlStreamFormat.empty else elements.reduce((acc, next) => acc.andThen(next))
}

object HtmlStreamOps {
  implicit def toSource(stream: HtmlStream): Source[Html, _] = stream.source.filter(!_.body.isEmpty)
  implicit def toHtmlStream(source: Source[Html, _]): HtmlStream = HtmlStream(source)
}