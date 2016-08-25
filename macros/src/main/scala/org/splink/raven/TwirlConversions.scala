package org.splink.raven

import org.splink.raven.BrickResult._
import play.api.Logger
import play.api.mvc.Cookie
import play.twirl.api.Html

import scala.util.Try

object TwirlConversions {
  private val logger = Logger(getClass).logger

  def combine(results: Seq[BrickResult])(template: Seq[Html] => Html) = {
    val htmls = results.map(r => Html(r.body))
    val htmlString = template(htmls).body
    combineAssets(results)(htmlString)
  }

  private def combineAssets(results: Seq[BrickResult]): (String) => BrickResult = {
    val (js, jsTop, css, cookies, metaTags) = results.foldLeft(
      Set.empty[Javascript], Set.empty[Javascript], Set.empty[Css], Seq.empty[Cookie], Set.empty[MetaTag]) { (acc, next) =>
      (acc._1 ++ next.js, acc._2 ++ next.jsTop, acc._3 ++ next.css, acc._4 ++ next.cookies, acc._5 ++ next.metaTags)
    }
    BrickResult(_, js, jsTop, css, cookies, metaTags)
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

  //TODO more info is needed: what pagelet threw the error?
  private def handle[A, B](t: Try[B], s: Seq[A], expectedSize: Int) = {
    if (s.size < expectedSize)
      throw new RuntimeException(s"Not enough children beneath the tree: (${s.mkString(",")})")
    else if (s.size > expectedSize)
      logger.warn(s"Found too many children beneath the tree: (${s.mkString(",")})")

    t.getOrElse(throw new RuntimeException("Error while rendering the template"))
  }

}
