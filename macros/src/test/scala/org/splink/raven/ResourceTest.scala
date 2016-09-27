package org.splink.raven

import org.scalatest.{Matchers, FlatSpec}

class ResourceTest extends FlatSpec with Matchers {

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
