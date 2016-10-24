package org.splink.pagelets

import org.scalatest.{Matchers, FlatSpec}

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

  "Tree#equals" should "identify equal Tree nodes" in {
    def combine(results: Seq[PageletResult]) = Tree.combine(results)

    val a = Tree('one, Seq.empty, combine)
    val b = Tree('one, Seq.empty, combine)

    a should equal(b)
  }

  it should "identify unequal Tree nodes" in {
    def combine(results: Seq[PageletResult]) = Tree.combine(results)

    val a = Tree('one, Seq.empty, combine)
    val b = Tree('two, Seq.empty, combine)

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
