package org.splink.raven

import org.scalatest.{Matchers, FlatSpec}

class PartTest extends FlatSpec with Matchers {

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
    val a = Tree('one, Seq.empty)
    val b = Tree('one, Seq.empty)

    a should equal(b)
  }

  it should "identify unequal Tree nodes" in {
    val a = Tree('one, Seq.empty)
    val b = Tree('two, Seq.empty)

    a should not equal b
  }

  "Tree#equals" should "identify equal Tree nodes when nested" in {
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
}
