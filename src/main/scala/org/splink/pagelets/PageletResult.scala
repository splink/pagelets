package org.splink.pagelets

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.mvc.Cookie

import scala.concurrent.Future
import scala.language.implicitConversions

object PageletResult {
  val empty = PageletResult(Source.empty[ByteString])
}

case class PageletResult(body: Source[ByteString, _],
                         js: Seq[Javascript] = Seq.empty,
                         jsTop: Seq[Javascript] = Seq.empty,
                         css: Seq[Css] = Seq.empty,
                         cookies: Seq[Future[Seq[Cookie]]] = Seq.empty,
                         metaTags: Seq[MetaTag] = Seq.empty)

