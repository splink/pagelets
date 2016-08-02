package org.splink.raven

import org.splink.raven.tree.PageletResult
import org.splink.raven.tree.PageletResult._
import play.api.Logger
import play.twirl.api.Html

import scala.util.Try

object TwirlConversions {

  def combineTwirl(results: Seq[PageletResult])(template: Seq[Html] => Html) = {
    val html = template(results.map(r => Html(r.body))).body
    combineAssets(results)(html)
  }


  implicit def adapt[A, B](f: A => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head)), s, required = 1)

  implicit def adapt[A, B](f: (A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1))), s, required = 2)

  implicit def adapt[A, B](f: (A, A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1), s(2))), s, required = 3)

  implicit def adapt[A, B](f: (A, A, A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1), s(2), s(3))), s, required = 4)

  implicit def adapt[A, B](f: (A, A, A, A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1), s(2), s(3), s(4))), s, required = 5)

  implicit def adapt[A, B](f: (A, A, A, A, A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1), s(2), s(3), s(4), s(5))), s, required = 6)

  implicit def adapt[A, B](f: (A, A, A, A, A, A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1), s(2), s(3), s(4), s(5), s(6))), s, required = 7)

  implicit def adapt[A, B](f: (A, A, A, A, A, A, A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1), s(2), s(3), s(4), s(5), s(6), s(7))), s, required = 8)

  implicit def adapt[A, B](f: (A, A, A, A, A, A, A, A, A) => B): Seq[A] => B =
    (s: Seq[A]) => handle(Try(f(s.head, s(1), s(2), s(3), s(4), s(5), s(6), s(7), s(8))), s, required = 9)

  def handle[A, B](t: Try[B], s: Seq[A], required: Int) = {
    if (s.size < required)
      throw new RuntimeException(s"Not enough children beneath the tree: (${s.mkString(",")})")
    else if (s.size > required)
      Logger.warn(s"Found too many children beneath the tree: (${s.mkString(",")})")

    t.getOrElse(throw new RuntimeException("Could not apply the ."))
  }

}
