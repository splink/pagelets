package org.splink.raven

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{EitherValues, FlatSpec, Matchers}
import org.splink.raven.Exceptions.TypeException
import play.api.mvc.{Action, Cookie, Results}
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext.Implicits.global

class LeafToolsTest extends FlatSpec with Matchers with ScalaFutures with EitherValues {

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val request = FakeRequest()

  val tools = new LeafToolsImpl with SerializerImpl

  "LeafTools#execute" should
    "successfully produce a Future if FunctionInfo's types fit the supplied args and it's fnc returns an Action[AnyContent]" in {

    def fnc(s: String) = Action(Results.Ok(s))

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") :: Nil)

    val leaf = Leaf('someId, info)
    val args = Seq(Arg("s", "Hello!"))

    val result = tools.leafOps(leaf).execute(info, args).futureValue

    result.body should equal("Hello!")
  }

  it should "produce a TypeException if FunctionInfo's types do not fit the supplied args" in {

    def fnc(s: String) = Action(Results.Ok(s))

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") :: Nil)

    val leaf = Leaf('someId, info)

    val result = tools.leafOps(leaf).execute(info, Seq.empty).eitherValue.get.left.get

    result shouldBe a[TypeException]
    result.getMessage should equal("'someId: 's:java.lang.String' not found in Arguments()")
  }

  it should "produce a TypeException if FunctionInfo.fnc requires more arguments then the execute function supports" in {

    def fnc(s0: String, s1: String, s2: String, s3: String, s4: String, s5: String, s6: String, s7: String, s8: String, s9: String, s10: String) = Action(Results.Ok(s0))

    val info = FunctionInfo(fnc _,
      ("s0", "java.lang.String") ::
        ("s1", "java.lang.String") ::
        ("s2", "java.lang.String") ::
        ("s3", "java.lang.String") ::
        ("s4", "java.lang.String") ::
        ("s5", "java.lang.String") ::
        ("s6", "java.lang.String") ::
        ("s7", "java.lang.String") ::
        ("s8", "java.lang.String") ::
        ("s9", "java.lang.String") ::
        ("s10", "java.lang.String") :: Nil)

    val leaf = Leaf('someId, info)
    val args = Seq(Arg("s0", "s0"), Arg("s1", "s1"), Arg("s2", "s2"), Arg("s3", "s3"), Arg("s4", "s4"),
      Arg("s5", "s5"), Arg("s6", "s6"), Arg("s7", "s7"), Arg("s8", "s8"), Arg("s9", "s9"), Arg("s10", "s10"))

    val result = tools.leafOps(leaf).execute(info, args).eitherValue.get.left.get

    result shouldBe a[IllegalArgumentException]
    result.getMessage should equal("'someId: too many arguments: 11")
  }

  it should "produce a failed Future which contains a TypeException if FunctionInfo's return type is not Action[AnyContent]" in {
    /*
      TODO wrapping the ClassCastException inside the failed Future would be preferred to the raw exception,
      even better: enforce the usage of functions which return Action[AnyContent] through the type system
      */
    def fnc = "hello"
    val info = FunctionInfo(fnc _, Nil)
    val leaf = Leaf('someId, info)

    an[ClassCastException] should be thrownBy tools.leafOps(leaf).execute(info, Seq.empty)
  }

  def leafOpsImpl = {
    val leaf = Leaf('someId, FunctionInfo(() => "unused", Nil))
    tools.leafOps(leaf).asInstanceOf[LeafToolsImpl#LeafOpsImpl]
  }

  "LeafTools#transform" should "yield the correct body" in {
    def fnc(s: String) = Action(Results.Ok(s))

    val result = leafOpsImpl.transform(fnc("Hi")).futureValue
    result.body should equal("Hi")
  }

  it should "yield the correct de-duplicated js" in {
    def fnc(s: String) = Action {
      Results.Ok(s).withHeaders(Javascript.name -> "a.js,b.js,b.js")
    }

    val result = leafOpsImpl.transform(fnc("Hi")).futureValue
    result.js should equal(Set(Javascript("a.js"), Javascript("b.js")))
  }

  it should "yield the correct de-duplicated jsTop" in {
    def fnc(s: String) = Action {
      Results.Ok(s).withHeaders(Javascript.nameTop -> "a.js,b.js,b.js")
    }

    val result = leafOpsImpl.transform(fnc("Hi")).futureValue

    result.js should equal(Set.empty)
    result.jsTop should equal(Set(Javascript("a.js"), Javascript("b.js")))
  }

  it should "yield the correct de-duplicated css" in {
    def fnc(s: String) = Action {
      Results.Ok(s).withHeaders(Css.name -> "a.css,b.css,b.css")
    }

    val result = leafOpsImpl.transform(fnc("Hi")).futureValue
    result.css should equal(Set(Css("a.css"), Css("b.css")))
  }

  it should "yield the correct de-duplicated metaTags" in {
    def fnc(s: String) = Action {
      val serializer = new SerializerImpl {}.serializer
      val s1 = serializer.serialize(MetaTag("key1", "value1")).right.get
      val s2 = serializer.serialize(MetaTag("key2", "value2")).right.get

      Results.Ok(s).withHeaders(MetaTag.name -> s"$s1,$s2,$s2")
    }

    val result = leafOpsImpl.transform(fnc("Hi")).futureValue
    result.metaTags should equal(Set(MetaTag("key1", "value1"), MetaTag("key2", "value2")))
  }

  it should "yield the correct de-duplicated cookies" in {
    def fnc(s: String) = Action {
      Results.Ok(s).withCookies(Cookie("cookie1", "value1"), Cookie("cookie1", "value2"))
    }

    val result = leafOpsImpl.transform(fnc("Hi")).futureValue
    result.cookies should equal(Seq(Cookie("cookie1", "value2")))
  }

  "LeafTools#values" should "extract the Arg values if the FunctionInfo.types align with the args" in {
    def fnc(s: String, d: Double) = s + d

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") ::("d", "scala.Double") :: Nil)
    val args = Seq(Arg("s", "hello"), Arg("d", 1d))

    leafOpsImpl.values(info, args).right.value should equal(Seq("hello", 1d))
  }

  it should "yield an ArgError if the types do not match" in {
    def fnc(s: String, d: Double) = s + d

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") ::("d", "scala.Int") :: Nil)
    val args = Seq(Arg("s", "hello"), Arg("d", 1d))

    leafOpsImpl.values(info, args).left.value.msg should equal(
      "'d:scala.Int' not found in Arguments(s:java.lang.String,d:scala.Double)")
  }

  it should "yield an ArgError if the names do not match" in {
    def fnc(s: String, d: Double) = s + d

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") ::("i", "scala.Double") :: Nil)
    val args = Seq(Arg("s", "hello"), Arg("d", 1d))

    leafOpsImpl.values(info, args).left.value.msg should equal(
      "'i:scala.Double' not found in Arguments(s:java.lang.String,d:scala.Double)"
    )
  }

  it should "yield an ArgError if args are missing" in {
    def fnc(s: String, d: Double) = s + d

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") ::("d", "scala.Double") :: Nil)
    val args = Seq(Arg("s", "hello"))

    leafOpsImpl.values(info, args).left.value.msg should equal(
      "'d:scala.Double' not found in Arguments(s:java.lang.String)"
    )
  }

}
