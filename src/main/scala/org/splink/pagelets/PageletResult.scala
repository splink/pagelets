package org.splink.pagelets

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.mvc.{Cookie, Flash, Session}

import scala.concurrent.Future
import scala.language.implicitConversions

object PageletResult {
  val empty = PageletResult(Source.empty[ByteString])
}

case class FailedPagelet(id: PageletId, t: Throwable)

case class PageletResult(body: Source[ByteString, _],
                         js: Seq[Javascript] = Seq.empty,
                         jsTop: Seq[Javascript] = Seq.empty,
                         css: Seq[Css] = Seq.empty,
                         results: Seq[Future[(Option[Flash], Option[Session], Seq[Cookie])]] = Seq.empty,
                         metaTags: Seq[MetaTag] = Seq.empty,
                         mandatoryFailedPagelets: Seq[Future[Boolean]] = Seq.empty) {
}

