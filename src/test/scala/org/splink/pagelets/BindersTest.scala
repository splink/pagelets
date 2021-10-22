package org.splink.pagelets

import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec

class BindersTest extends AnyFlatSpec with Matchers {

  import Binders._

  "PathBindablePageletId" should "bind a String to a PageletId" in {
    PathBindablePageletId.bind("one", "oneValue").toOption.get should equal(PageletId("oneValue"))
  }

  it should "bind a String which begins with an Int to a PageletId" in {
    PathBindablePageletId.bind("one", "1").toOption.get should equal(PageletId("1"))
  }

  it should "unbind a String from a PageletId" in {
    PathBindablePageletId.unbind("one", PageletId("oneValue")) should equal("oneValue")
  }

  it should "unbind a String which begins with an Int from a PageletId" in {
    PathBindablePageletId.unbind("one", PageletId("1")) should equal("1")
  }
}
