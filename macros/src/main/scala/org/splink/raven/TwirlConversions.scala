package org.splink.raven

import org.splink.raven.PageletResult._
import play.api.Logger
import play.twirl.api.Html

import scala.util.Try

object TwirlConversions {

  def combine(results: Seq[PageletResult])(template: Seq[Html] => Html) = {
    val htmls = results.map(r => Html(r.body))
    val htmlString = template(htmls).body
    combineAssets(results)(htmlString)
  }

  private def combineAssets(results: Seq[PageletResult]): (String) => PageletResult = {
    val (js, css) = results.foldLeft(Set.empty[Javascript], Set.empty[Css]) { (acc, next) =>
      (acc._1 ++ next.js, acc._2 ++ next.css)
    }
    PageletResult(_, js, css)
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
      Logger.warn(s"Found too many children beneath the tree: (${s.mkString(",")})")

    t.getOrElse(throw new RuntimeException("Error while rendering the template"))
  }

}
