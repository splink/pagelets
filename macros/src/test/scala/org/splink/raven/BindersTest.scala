package org.splink.raven

import org.scalatest.{Matchers, FlatSpec}

class BindersTest extends FlatSpec with Matchers {

  import Binders._

  "PathBindableSymbol" should "bind a String to a Symbol" in {
    PathBindableSymbol.bind("one", "oneValue").right.get should equal('oneValue)
  }

  it should "bind a String which begins with an Int to a Symbol" in {
    PathBindableSymbol.bind("one", "1").right.get should equal(Symbol("1"))
  }

  it should "unbind a String to a Symbol" in {
    PathBindableSymbol.unbind("one", 'oneValue) should equal("oneValue")
  }

  it should "unbind a String which begins with an Int to a Symbol" in {
    PathBindableSymbol.unbind("one", Symbol("1")) should equal("1")
  }
}
