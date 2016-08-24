package org.splink.raven

import org.splink.raven.BrickResult.{MetaTag, Css, Javascript}
import play.api.http.{ContentTypeOf, ContentTypes, Writeable}
import play.api.mvc.{Codec, Cookie, Result}

object BrickResult {

  sealed trait Resource {
    def src: String
  }

  case object Javascript {
    val name: String = "js"
  }

  case class Javascript(src: String) extends Resource

  case object Css {
    val name: String = "css"
  }

  case class Css(src: String) extends Resource

  case object MetaTag {
    val name: String = "meta"
  }

  case class MetaTag(name: String, content: String)

  val empty = BrickResult("")

  implicit class ResultOps(result: Result) {
    def withJavascript(js: Javascript*) = helper(js.map(_.src), Javascript.name)

    def withCss(css: Css*) = helper(css.map(_.src), Css.name)

    def withMetaTags(tags: MetaTag*) =
      result.withHeaders(MetaTag.name -> tags.map(Serializer.serialize).mkString("\n"))

    private def helper(elems: Seq[String], id: String) =
      result.withHeaders(s"$id" -> elems.mkString(","))
  }

  implicit val ct: ContentTypeOf[BrickResult] =
    ContentTypeOf[BrickResult](Some(ContentTypes.HTML))

  implicit def writeableOf(implicit codec: Codec, ct: ContentTypeOf[BrickResult]): Writeable[BrickResult] =
    Writeable(result => codec.encode(result.body.trim))
}

case class BrickResult(body: String,
                       js: Set[Javascript] = Set.empty,
                       css: Set[Css] = Set.empty,
                       cookies: Seq[Cookie] = Seq.empty,
                       metaTags: Set[MetaTag] = Set.empty)