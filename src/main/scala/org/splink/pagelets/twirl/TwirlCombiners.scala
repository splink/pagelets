package org.splink.pagelets.twirl

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.splink.pagelets._
import play.api.Logger
import play.api.mvc.{Cookie, Flash, Session}
import play.twirl.api.{Html, HtmlFormat}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.Try

object TwirlCombiners {
  private val log = Logger("TwirlConversions")

  def combine(results: Seq[PageletResult])(template: Seq[Html] => Html)(
    implicit ec: ExecutionContext, m: Materializer): PageletResult = {

    val htmls = Future.traverse(results) { r =>
      r.body.runFold(HtmlFormat.empty)((acc, next) => Html(acc.toString + next.utf8String))
    }

    val source = Source.future(htmls.map(xs => ByteString(template(xs).body)))

    combineAssets(results)(source)
  }

  def combineStream(results: Seq[PageletResult])(template: Seq[HtmlStream] => HtmlStream)(
    implicit ec: ExecutionContext, m: Materializer): PageletResult = {

    def toHtmlStream(results: Seq[PageletResult]) =
      results.map(r => HtmlStream(r.body.map(b => Html(b.utf8String))))

    val stream = template(toHtmlStream(results))
    val source = stream.source.map(s => ByteString(s.body))

    combineAssets(results)(source)
  }

  private def combineAssets(results: Seq[PageletResult]): Source[ByteString, _] => PageletResult = {
    val (js, jsTop, css, res, metaTags, failedPagelets) = results.foldLeft(
      Seq.empty[Javascript],
      Seq.empty[Javascript],
      Seq.empty[Css],
      Seq.empty[Future[(Option[Flash], Option[Session], Seq[Cookie])]],
      Seq.empty[MetaTag],
      Seq.empty[Future[Boolean]]) { (acc, next) =>
      (acc._1 ++ next.js,
        acc._2 ++ next.jsTop,
        acc._3 ++ next.css,
        acc._4 ++ next.results,
        (acc._5 ++ next.metaTags).distinct,
        acc._6 ++ next.mandatoryFailedPagelets)
    }

    PageletResult(_, js, jsTop, css, res, metaTags, failedPagelets)
  }

  implicit def adapt[A, B](f: A => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head)), s, expectedSize = 1)

  implicit def adapt[A, B](f: (A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1))), s, expectedSize = 2)

  implicit def adapt[A, B](f: (A, A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1), s(2))), s, expectedSize = 3)

  implicit def adapt[A, B](f: (A, A, A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1), s(2), s(3))), s, expectedSize = 4)

  implicit def adapt[A, B](f: (A, A, A, A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1), s(2), s(3), s(4))), s, expectedSize = 5)

  implicit def adapt[A, B](f: (A, A, A, A, A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1), s(2), s(3), s(4), s(5))), s, expectedSize = 6)

  implicit def adapt[A, B](f: (A, A, A, A, A, A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1), s(2), s(3), s(4), s(5), s(6))), s, expectedSize = 7)

  implicit def adapt[A, B](f: (A, A, A, A, A, A, A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1), s(2), s(3), s(4), s(5), s(6), s(7))), s, expectedSize = 8)

  implicit def adapt[A, B](f: (A, A, A, A, A, A, A, A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1), s(2), s(3), s(4), s(5), s(6), s(7), s(8))), s, expectedSize = 9)

  private def handle[A, B](t: Try[B], s: Seq[A], expectedSize: Int) = {
    if (s.size < expectedSize)
      throw new RuntimeException(s"Not enough children beneath the tree: (${s.mkString(",")})")
    else if (s.size > expectedSize)
      log.warn(s"Found too many children beneath the tree: (${s.mkString(",")})")

    t.getOrElse(throw new RuntimeException("Error while rendering the template"))
  }

}
