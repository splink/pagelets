package org.splink.pagelets

import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec

class ResourceTest extends AnyFlatSpec with Matchers {

  "Javascript.name" should "return 'js'" in {
    Javascript.name should equal("js")
  }

  "Javascript.nameTop" should "return 'jsTop'" in {
    Javascript.nameTop should equal("jsTop")
  }

  "Css.name" should "return 'css'" in {
    Css.name should equal("css")
  }

  "MetaTag.name" should "return 'meta'" in {
    MetaTag.name should equal("meta")
  }
}
