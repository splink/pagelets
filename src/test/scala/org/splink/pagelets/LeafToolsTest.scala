package org.splink.pagelets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import helpers.FutureHelper
import org.scalatest.mock.MockitoSugar
import org.scalatest.{EitherValues, FlatSpec, Matchers}
import org.splink.pagelets.Exceptions.TypeException
import play.api.mvc.{Action, Cookie, Results}
import play.api.test.FakeRequest

class LeafToolsTest extends FlatSpec with Matchers with FutureHelper with EitherValues with MockitoSugar {

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val request = FakeRequest()

  val tools = new LeafToolsImpl {}

  "LeafTools#execute" should
    "successfully produce a Future if FunctionInfo's types fit the args and it's fnc returns an Action[AnyContent]" in {

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

    val result = tools.leafOps(leaf).execute(info, Seq.empty).futureTry.failed.get

    result shouldBe a[TypeException]
    result.getMessage should equal("'someId: 's:java.lang.String' not found in Arguments()")
  }

  it should "produce a TypeException if FunctionInfo.fnc requires more arguments then the execute function supports" in {

    def fnc(s0: String, s1: String, s2: String, s3: String, s4: String, s5: String,
            s6: String, s7: String, s8: String, s9: String, s10: String) = Action(Results.Ok(s0))

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

    val result = tools.leafOps(leaf).execute(info, args).futureTry.failed.get

    result shouldBe a[IllegalArgumentException]
    result.getMessage should equal("'someId: too many arguments: 11")
  }

  it should "fail with a ClassCastException if FunctionInfo's return type is not Action[AnyContent]" in {
    /*
      This should not happen when FunctionMacros is used. FunctionMacros raises a compile error if the
      supplied function which is converted to FunctionInfo does not return Action[_]
      */
    def fnc = "hello"
    val info = FunctionInfo(fnc _, Nil)
    val leaf = Leaf('someId, info)

    an[ClassCastException] should be thrownBy tools.leafOps(leaf).execute(info, Seq.empty)
  }

  def leafOps = {
    val leaf = Leaf('someId, FunctionInfo(() => "unused", Nil))
    tools.leafOps(leaf).asInstanceOf[LeafToolsImpl#LeafOpsImpl]
  }

  "LeafTools#transform" should "yield the correct body" in {
    def fnc(s: String) = Action(Results.Ok(s))

    val result = leafOps.transform(fnc("Hi")).futureValue
    result.body should equal("Hi")
  }

  it should "yield the correct de-duplicated cookies" in {
    def fnc(s: String) = Action {
      Results.Ok(s).withCookies(Cookie("cookie1", "value1"), Cookie("cookie1", "value2"))
    }

    val result = leafOps.transform(fnc("Hi")).futureValue
    result.cookies should equal(Seq(Cookie("cookie1", "value2")))
  }

  "LeafTools#values" should "extract the Arg values if the FunctionInfo.types align with the args" in {
    def fnc(s: String, d: Double) = s + d

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") ::("d", "scala.Double") :: Nil)
    val args = Seq(Arg("s", "hello"), Arg("d", 1d))

    leafOps.values(info, args).right.value should equal(Seq("hello", 1d))
  }

  it should "extract the Arg values if there are more args then types" in {
    def fnc(s: String, d: Double) = s + d

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") ::("d", "scala.Double") :: Nil)
    val args = Seq(Arg("s", "hello"), Arg("d", 1d), Arg("i", 1))

    leafOps.values(info, args).right.value should equal(Seq("hello", 1d))
  }

  it should "yield an ArgError if the types do not match" in {
    def fnc(s: String, d: Double) = s + d

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") ::("d", "scala.Int") :: Nil)
    val args = Seq(Arg("s", "hello"), Arg("d", 1d))

    leafOps.values(info, args).left.value.msg should equal(
      "'d:scala.Int' not found in Arguments(s:java.lang.String,d:scala.Double)")
  }

  it should "yield an ArgError if the names do not match" in {
    def fnc(s: String, d: Double) = s + d

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") ::("i", "scala.Double") :: Nil)
    val args = Seq(Arg("s", "hello"), Arg("d", 1d))

    leafOps.values(info, args).left.value.msg should equal(
      "'i:scala.Double' not found in Arguments(s:java.lang.String,d:scala.Double)"
    )
  }

  it should "yield an ArgError if args are missing" in {
    def fnc(s: String, d: Double) = s + d

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") ::("d", "scala.Double") :: Nil)
    val args = Seq(Arg("s", "hello"))

    leafOps.values(info, args).left.value.msg should equal(
      "'d:scala.Double' not found in Arguments(s:java.lang.String)"
    )
  }

  "LeafTools#scalaClassNameFor" should "return the classname for Int" in {
    val name = leafOps.scalaClassNameFor(1)
    name should equal("scala.Int")
  }

  it should "return the classname for String" in {
    val name = leafOps.scalaClassNameFor("123")
    name should equal("java.lang.String")
  }

  it should "return the classname for Double" in {
    val name = leafOps.scalaClassNameFor(1d)
    name should equal("scala.Double")
  }

  it should "return the classname for Float" in {
    val name = leafOps.scalaClassNameFor(1f)
    name should equal("scala.Float")
  }

  it should "return the classname for Long" in {
    val name = leafOps.scalaClassNameFor(1l)
    name should equal("scala.Long")
  }

  it should "return the classname for Short" in {
    val name = leafOps.scalaClassNameFor(1.toShort)
    name should equal("scala.Short")
  }

  it should "return the classname for Byte" in {
    val name = leafOps.scalaClassNameFor(1.toByte)
    name should equal("scala.Byte")
  }

  it should "return the classname for Boolean" in {
    val name = leafOps.scalaClassNameFor(true)
    name should equal("scala.Boolean")
  }

  it should "return the classname for Char" in {
    val name = leafOps.scalaClassNameFor('a')
    name should equal("scala.Char")
  }

  it should "return the classname for Symbol" in {
    val name = leafOps.scalaClassNameFor('someSymbol)
    name should equal("scala.Symbol")
  }

  it should "return 'undefined' for any local class without a canonical name" in {
    case class Test(name: String)
    val name = leafOps.scalaClassNameFor(Test("yo"))
    name should equal("undefined")
  }

  case class Test2(name: String)

  it should "return the classname for any custom class" in {
    val name = leafOps.scalaClassNameFor(Test2("yo"))
    name should equal("org.splink.pagelets.LeafToolsTest.Test2")
  }

  it should "return the Option classname Some[_]" in {
    val name = leafOps.scalaClassNameFor(Option("yo"))
    name should equal("scala.Option")
  }

  it should "return the Option classname for None" in {
    val name = leafOps.scalaClassNameFor(None)
    name should equal("scala.Option")
  }

  "LeafTools#eitherSeq" should "convert the whole Seq if there are no Left" in {
    val xs = Seq(Right("One"), Right("Two"), Right("Three"))

    val result = leafOps.eitherSeq(xs)
    result should equal(Right(Seq("One", "Two", "Three")))
  }

  it should "produce the last Left if the given Seq contains one" in {
    val xs = Seq(Right("One"), Left("Oops"), Left("Oops2"), Right("four"))

    val result = leafOps.eitherSeq(xs)
    result should equal(Left("Oops2"))
  }

}
