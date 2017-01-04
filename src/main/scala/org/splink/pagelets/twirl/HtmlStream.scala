package org.splink.pagelets.twirl

import akka.stream.scaladsl.{Concat, Source}
import org.splink.pagelets.{Fingerprint, Head, PageStream}
import play.twirl.api.{Appendable, Format, Html, HtmlFormat}


case class HtmlPageStream(language: String, head: Head, body: HtmlStream, js: Option[Fingerprint] = None)

class HtmlStream(val source: Source[Html, _]) extends Appendable[HtmlStream]

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
    if (elements.isEmpty) HtmlStreamFormat.empty else elements.reduce((acc, next) =>
      HtmlStream {
        Source.combine(acc.source, next.source)(Concat.apply)
      })
}

object HtmlStreamOps {
  implicit def toSource(stream: HtmlStream): Source[Html, _] = stream.source.filter(_.body.nonEmpty)

  implicit def toHtmlStream(source: Source[Html, _]): HtmlStream = HtmlStream(source)


  implicit def pageStream2HtmlPageStream(page: PageStream): HtmlPageStream =
    HtmlPageStream(page.language, page.head, HtmlStream(page.body.map(b => Html(b.utf8String))), page.js)
}

