package org.splink.pagelets

import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}
import play.api.{Environment, Mode}

import scala.language.reflectiveCalls

class ResourcesTest extends FlatSpec with Matchers with BeforeAndAfter {

  implicit val env = Environment.simple()

  val resources = new ResourcesImpl {}.resources

  before {
    resources.clear()
  }

  def mkFingerprint = resources.update(Seq(Javascript("a.js"), Javascript("b.js")))
  val expectedPrint = Fingerprint(mkFingerprint.get.toString)

  "Resources#update" should "return a fingerprint for a Set of resources" in {
    mkFingerprint shouldBe Some(expectedPrint)
  }

  it  should "return a fingerprint for a Set of resources, even if one of the resources is missing" in {
    val print = resources.update(Seq(Javascript("a.js"), Javascript("b.js"), Javascript("missing.js")))
    print shouldBe Some(expectedPrint)
  }

  "Resources#contains" should "return true, if there is an assembled Resource for the fingerprint" in {
    mkFingerprint
    resources.contains(expectedPrint) shouldBe true
  }

  it should "return false, if there is no resource for the fingerprint" in {
    resources.contains(expectedPrint) shouldBe false
  }

  "Resources.assemble" should "combine Javascript resources and filter duplicates" in {
    val s = Seq(Javascript("a.js"), Javascript("a.js"), Javascript("b.js"))
    resources.assemble(s) shouldBe ResourceContent(
      """console.log("a");
        |console.log("b");
        |""".stripMargin, JsMimeType)
  }

  it should "combine Css resources and filter duplicates" in {
    val s = Seq(Css("a.css"), Css("a.css"))
    resources.assemble(s) shouldBe ResourceContent(
      """body {
        |    text-align: center;
        |}
        |""".stripMargin, CssMimeType)
  }

  "Resources.contentFor" should "return the content and mime type, if there is an assembled Resource for the fingerprint" in {
    mkFingerprint
    resources.contentFor(expectedPrint) shouldBe Some(ResourceContent(
      """console.log("a");
        |console.log("b");
        |""".stripMargin, JsMimeType))
  }

  it should "return None if there is no assembled resource for the fingerprint" in {
    resources.contentFor(expectedPrint) shouldBe None
  }

  it should "return None, if there is no resource for the fingerprint" in {
    resources.contentFor(expectedPrint) shouldBe None
  }

  "Resources#load" should "load an existing Javascript resource and detect it's mime type" in {
    resources.load(Javascript("a.js")) shouldBe Some(
      ResourceContent(
        """console.log("a");
          |""".stripMargin, JsMimeType))
  }

  it should "return None if the requested resource does not exist" in {
    resources.load(Javascript("missing.js")) shouldBe None
  }

  it should "load an existing Css resource and detect it's mime type" in {
    resources.load(Css("a.css")) shouldBe Some(
      ResourceContent(
        """body {
          |    text-align: center;
          |}
          |""".stripMargin, CssMimeType))
  }

  "Resources#maybeCachedContent" should "return from cache if the resource is cached and we're in prod mode" in {
    val e = Environment.simple(mode = Mode.Prod)
    mkFingerprint
    resources.maybeCachedContent(Javascript("a.js"))(e) shouldBe Some(
      ResourceContent(
        """console.log("a");
          |""".stripMargin, JsMimeType))
  }

  it should "not return from cache if we're not in prod mode" in {
    val e = Environment.simple(mode = Mode.Dev)
    mkFingerprint
    resources.maybeCachedContent(Javascript("a.js"))(e) shouldBe None
  }

  it should "not return from cache if the item is not cached" in {
    val e = Environment.simple(mode = Mode.Prod)
    resources.maybeCachedContent(Javascript("a.js"))(e) shouldBe None
  }
}