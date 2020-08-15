package org.splink.pagelets

import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec

class BindersTest extends AnyFlatSpec with Matchers {

  import Binders._

  "PathBindableSymbol" should "bind a String to a Symbol" in {
    PathBindableSymbol.bind("one", "oneValue").toOption.get should equal(Symbol("oneValue"))
  }

  it should "bind a String which begins with an Int to a Symbol" in {
    PathBindableSymbol.bind("one", "1").toOption.get should equal(Symbol("1"))
  }

  it should "unbind a String from a Symbol" in {
    PathBindableSymbol.unbind("one", Symbol("oneValue")) should equal("oneValue")
  }

  it should "unbind a String which begins with an Int from a Symbol" in {
    PathBindableSymbol.unbind("one", Symbol("1")) should equal("1")
  }
}
