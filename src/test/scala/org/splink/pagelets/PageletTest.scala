package org.splink.pagelets

import org.scalatest.{FlatSpec, Matchers}

class PageletTest extends FlatSpec with Matchers {

  "Leaf#equals" should "identify equal Leaf nodes" in {
    val fnc = () => "someFunction"
    val a = Leaf('one, FunctionInfo(fnc, Nil))
    val b = Leaf('one, FunctionInfo(fnc, Nil))

    a should equal(b)
  }

  it should "identify unequal Leaf nodes" in {
    val fnc = () => "someFunction"
    val a = Leaf('one, FunctionInfo(fnc, Nil))
    val b = Leaf('two, FunctionInfo(fnc, Nil))

    a should not equal b
  }
  //TODO deduplication happens somewhere else now!
/*
  "Tree#combine" should "deduplicate metaTags" in {
    val r1 = PageletResult(body("b1"), metaTags = Seq(MetaTag("meta", "tag"), MetaTag("meta", "tag")))
    val r2 = PageletResult(body("b2"), metaTags = Seq(MetaTag("meta", "tag"), MetaTag("meta1", "tag1")))

    val result = Tree.combine(Seq(r1, r2))

    result.metaTags should equal(Seq(MetaTag("meta", "tag"), MetaTag("meta1", "tag1")))
  }

  it should "deduplicate cookies" in {
    val r1 = PageletResult(body("b1"), cookies = Seq(Cookie("cookie", "v1"), Cookie("cookie", "v2")))
    val r2 = PageletResult(body("b2"), cookies = Seq(Cookie("cookie", "v1"), Cookie("cookie", "v3")))

    val result = Tree.combine(Seq(r1, r2))

    result.cookies should equal(Seq(Cookie("cookie", "v1"), Cookie("cookie", "v2"), Cookie("cookie", "v3")))
  }
*/
  "Tree#equals" should "identify equal Tree nodes" in {
    val a = Tree('one, Seq.empty, Tree.combine)
    val b = Tree('one, Seq.empty, Tree.combine)

    a should equal(b)
  }

  it should "identify unequal Tree nodes" in {
    val a = Tree('one, Seq.empty, Tree.combine)
    val b = Tree('two, Seq.empty, Tree.combine)

    a should not equal b
  }

  it should "identify equal Tree nodes when nested" in {
    val fnc = () => "someFunction"
    val l1 = Leaf('one, FunctionInfo(fnc, Nil))
    val l2 = Leaf('two, FunctionInfo(fnc, Nil))

    val a = Tree('one, Seq(l1, l2))
    val b = Tree('one, Seq(l1, l2))

    a should equal(b)
  }

  it should "identify unequal Tree nodes when nested" in {
    val fnc = () => "someFunction"
    val l1 = Leaf('one, FunctionInfo(fnc, Nil))
    val l2 = Leaf('two, FunctionInfo(fnc, Nil))

    val a = Tree('one, Seq(l1, l2))
    val b = Tree('one, Seq(l1))

    a should not equal b
  }

  it should "identify unequal Tree nodes when nested (2)" in {
    val fnc = () => "someFunction"
    val l1 = Leaf('one, FunctionInfo(fnc, Nil))
    val l2 = Leaf('two, FunctionInfo(fnc, Nil))
    val l3 = Leaf('three, FunctionInfo(fnc, Nil))

    val a = Tree('one, Seq(l1, l2))
    val b = Tree('one, Seq(l1, l3))

    a should not equal b
  }

  "Tree#copy" should "copy the whole tree" in {
    def combine(results: Seq[PageletResult]) = Tree.combine(results)
    val combineFnc = combine _

    val a = Tree('one, Seq.empty, combineFnc)
    val b =  a.copy(id = 'two)

    a.id should equal('one)
    b.id should equal('two)
    b.children should equal(Seq.empty)
    b.combine should equal(combineFnc)
  }
}
