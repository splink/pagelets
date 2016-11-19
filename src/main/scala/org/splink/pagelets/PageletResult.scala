package org.splink.pagelets

import play.api.http.{ContentTypeOf, ContentTypes, Writeable}
import play.api.mvc.{Codec, Cookie}

import scala.language.implicitConversions

object PageletResult {
  val empty = PageletResult("")
  implicit val ct: ContentTypeOf[PageletResult] =
    ContentTypeOf[PageletResult](Some(ContentTypes.HTML))

  implicit def writeableOf(implicit codec: Codec, ct: ContentTypeOf[PageletResult]): Writeable[PageletResult] =
    Writeable(result => codec.encode(result.body.trim))
}

case class PageletResult(body: String,
                         js: Seq[Javascript] = Seq.empty,
                         jsTop: Seq[Javascript] = Seq.empty,
                         css: Seq[Css] = Seq.empty,
                         cookies: Seq[Cookie] = Seq.empty,
                         metaTags: Seq[MetaTag] = Seq.empty)

