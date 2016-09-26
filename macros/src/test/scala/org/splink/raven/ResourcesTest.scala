package org.splink.raven

import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}
import org.splink.raven.Resources.{Fingerprint, JsMimeType, ResourceContent}
import play.api.{Environment, Mode}

class ResourcesTest extends FlatSpec with Matchers with BeforeAndAfter {

  implicit val env = Environment.simple()

  val resourceProvider = Resources().asInstanceOf[Resources.ResourceProviderImpl]

  before {
    resourceProvider.clear()
  }

  def mkFingerprint = resourceProvider.update(Set(Javascript("a.js"), Javascript("b.js")))
  val expectedPrint = Fingerprint("4c59af0174a6f1a76779abd4eb83aa07")

  "Resources.update" should "return a fingerprint for a Set of resources" in {
    mkFingerprint shouldBe Some(expectedPrint)
  }

  it  should "return a fingerprint for a Set of resources, even if one of the resources is missing" in {
    val print = resourceProvider.update(Set(Javascript("a.js"), Javascript("b.js"), Javascript("missing.js")))
    print shouldBe Some(expectedPrint)
  }

  "Resources.contains" should "return true, if there is an assembled Resource for the fingerprint" in {
    mkFingerprint
    resourceProvider.contains(expectedPrint) shouldBe true
  }

  it should "return false, if there is no resource for the fingerprint" in {
    resourceProvider.contains(expectedPrint) shouldBe false
  }

  "Resources.contentFor" should "return the content and mime type, if there is an assembled Resource for the fingerprint" in {
    mkFingerprint
    resourceProvider.contentFor(expectedPrint) shouldBe Some(ResourceContent(
      """console.log("a");
        |console.log("b");
        |""".stripMargin, JsMimeType))
  }

  it should "return none if thre is no assembled resource for the fingerprint" in {
    resourceProvider.contentFor(expectedPrint) shouldBe None
  }

  it should "return None, if there is no resource for the fingerprint" in {
    resourceProvider.contentFor(expectedPrint) shouldBe None
  }

  "Resources.clear()" should "clear the itemCache and the cache" in {
    mkFingerprint
    resourceProvider.clear()
    resourceProvider.cache should equal(Map.empty)
    resourceProvider.itemCache should equal(Map.empty)
  }

  "Resources.load" should "load an existing Javascript resource and detect it's mime type" in {
    resourceProvider.load(Javascript("a.js")) shouldBe Some(
      ResourceContent(
        """console.log("a");
          |""".stripMargin, JsMimeType))
  }

  it should "return None if the requested resource does not exist" in {
    resourceProvider.load(Javascript("missing.js")) shouldBe None
  }

  it should "load an existing Css resource and detect it's mime type" in {
    resourceProvider.load(Javascript("a.css")) shouldBe Some(
      ResourceContent(
        """body {
          |    text-align: center;
          |}
          |""".stripMargin, JsMimeType))
  }

  "Resources.maybeCachedContent" should "return from cache if the resource is cached and we're in prod mode" in {
    val e = Environment.simple(mode = Mode.Prod)
    mkFingerprint
    resourceProvider.maybeCachedContent(Javascript("a.js"))(e) shouldBe Some(
      ResourceContent(
        """console.log("a");
          |""".stripMargin, JsMimeType))
  }

  it should "not return from cache if we're not in prod mode" in {
    val e = Environment.simple(mode = Mode.Dev)
    mkFingerprint
    resourceProvider.maybeCachedContent(Javascript("a.js"))(e) shouldBe None
  }

  it should "not return from cache if the item is not cached" in {
    val e = Environment.simple(mode = Mode.Prod)
    resourceProvider.maybeCachedContent(Javascript("a.js"))(e) shouldBe None
  }

}
