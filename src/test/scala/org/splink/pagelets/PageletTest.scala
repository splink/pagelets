package org.splink.pagelets

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import helpers.FutureHelper
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec
import play.api.mvc.{Cookie, Flash, Session}

import scala.concurrent.Future

class PageletTest extends AnyFlatSpec with Matchers with FutureHelper {
  implicit val system = ActorSystem()

  "Leaf#equals" should "identify equal Leaf nodes" in {
    val fnc = () => "someFunction"
    val a = Leaf(PageletId("one"), FunctionInfo(fnc, Nil))
    val b = Leaf(PageletId("one"), FunctionInfo(fnc, Nil))

    a should equal(b)
  }

  it should "identify unequal Leaf nodes" in {
    val fnc = () => "someFunction"
    val a = Leaf(PageletId("one"), FunctionInfo(fnc, Nil))
    val b = Leaf(PageletId("two"), FunctionInfo(fnc, Nil))

    a should not equal b
  }

  "Tree#equals" should "identify equal Tree nodes" in {
    val a = Tree(PageletId("one"), Seq.empty, Tree.combine)
    val b = Tree(PageletId("one"), Seq.empty, Tree.combine)

    a should equal(b)
  }

  it should "identify unequal Tree nodes" in {
    val a = Tree(PageletId("one"), Seq.empty, Tree.combine)
    val b = Tree(PageletId("two"), Seq.empty, Tree.combine)

    a should not equal b
  }

  it should "identify equal Tree nodes when nested" in {
    val fnc = () => "someFunction"
    val l1 = Leaf(PageletId("one"), FunctionInfo(fnc, Nil))
    val l2 = Leaf(PageletId("two"), FunctionInfo(fnc, Nil))

    val a = Tree(PageletId("one"), Seq(l1, l2))
    val b = Tree(PageletId("one"), Seq(l1, l2))

    a should equal(b)
  }

  it should "identify unequal Tree nodes when nested" in {
    val fnc = () => "someFunction"
    val l1 = Leaf(PageletId("one"), FunctionInfo(fnc, Nil))
    val l2 = Leaf(PageletId("two"), FunctionInfo(fnc, Nil))

    val a = Tree(PageletId("one"), Seq(l1, l2))
    val b = Tree(PageletId("one"), Seq(l1))

    a should not equal b
  }

  it should "identify unequal Tree nodes when nested (2)" in {
    val fnc = () => "someFunction"
    val l1 = Leaf(PageletId("one"), FunctionInfo(fnc, Nil))
    val l2 = Leaf(PageletId("two"), FunctionInfo(fnc, Nil))
    val l3 = Leaf(PageletId("three"), FunctionInfo(fnc, Nil))

    val a = Tree(PageletId("one"), Seq(l1, l2))
    val b = Tree(PageletId("one"), Seq(l1, l3))

    a should not equal b
  }

  "Tree#copy" should "copy the whole tree" in {
    def combine(results: Seq[PageletResult]) = Tree.combine(results)
    val combineFnc = combine _

    val a = Tree(PageletId("one"), Seq.empty, combineFnc)
    val b =  a.copy(id = PageletId("two"))

    a.id should equal(PageletId("one"))
    b.id should equal(PageletId("two"))
    b.children should equal(Seq.empty)
    b.combine should equal(combineFnc)
  }

  "Tree#combine" should "combine all PageResult properties" in {
    val r1 = PageletResult(
      Source.single(ByteString("body")),
      Seq(Javascript("src.js")),
      Seq(Javascript("src-top.js")),
      Seq(Css("src.css")),
      Seq(Future.successful {
        (Some(Flash(Map("f" -> "g"))), Some(Session(Map("a" -> "b", "a" -> "c"))), Seq(Cookie("name", "value")))
      }),
      Seq(MetaTag("name", "content")),
      Seq(Future.successful(true)))

    val r2 = PageletResult(
      Source.single(ByteString("body2")),
      Seq(Javascript("src2.js")),
      Seq(Javascript("src2-top.js")),
      Seq(Css("src2.css")),
      Seq(Future.successful {
        (None, Some(Session(Map("a" -> "b1", "b" -> "c"))), Seq(Cookie("name2", "value")))
      }),
      Seq(MetaTag("name2", "content")),
      Seq(Future.successful(false)))

    val result = Tree.combine(Seq(r1, r2))
    result.js should equal(Seq(Javascript("src.js"), Javascript("src2.js")))
    result.jsTop should equal(Seq(Javascript("src-top.js"), Javascript("src2-top.js")))
    result.css should equal(Seq(Css("src.css"), Css("src2.css")))

    result.results.map(_.futureValue) should equal {
      Seq((Some(Flash(Map("f" -> "g"))), Some(Session(Map("a" -> "b", "a" -> "c"))), Seq(Cookie("name", "value"))),
        (None, Some(Session(Map("a" -> "b1", "b" -> "c"))), Seq(Cookie("name2", "value"))))
    }

    result.metaTags should equal(Seq(MetaTag("name", "content"), MetaTag("name2", "content")))
    result.mandatoryFailedPagelets.map(_.futureValue) should equal(Seq(true, false))
  }
}
