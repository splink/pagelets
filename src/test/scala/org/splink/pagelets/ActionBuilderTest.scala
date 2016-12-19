package org.splink.pagelets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import helpers.FutureHelper
import org.scalatest.mock.MockitoSugar
import org.scalatest.{EitherValues, FlatSpec, Matchers}
import org.splink.pagelets.Exceptions.TypeException
import play.api.mvc.{Action, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._

class ActionBuilderTest extends FlatSpec with Matchers with FutureHelper with EitherValues with MockitoSugar {

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val request = FakeRequest()

  val tools = new ActionBuilderImpl {}

  "ActionService#execute" should
    "produce an Action if FunctionInfo's types fit the args with primitive args" in {

    def fnc(s: String) = Action(Results.Ok(s))

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") :: Nil)

    val args = Seq(Arg("s", "Hello!"))

    val action = tools.actionService.execute('someId, info, args).right.get
    contentAsString(action(request)) should equal("Hello!")
  }

  it should "produce an Action if FunctionInfo's types fit the args with optional args" in {

    def fnc(o: Option[String]) = Action(Results.Ok(o.toString))

    val info = FunctionInfo(fnc _, ("o", "scala.Option") :: Nil)

    val args = Seq(Arg("o", Some("optional")))

    val action = tools.actionService.execute('someId, info, args).right.get
    contentAsString(action(request)) should equal("Some(optional)")
  }

  it should "produce an Action if FunctionInfo's types fit the args with multiple different args" in {

    def fnc(i: Int, o: Option[String], custom: Test2) = Action(Results.Ok(i.toString + o.toString + custom.toString))

    val info = FunctionInfo(fnc _, ("i", "scala.Int") :: ("o", "scala.Option") :: ("custom", "org.splink.pagelets.ActionBuilderTest.Test2") :: Nil)

    val args = Seq(Arg("i", 1), Arg("o", Some("optional")), Arg("custom", Test2("custom")))

    val action = tools.actionService.execute('someId, info, args).right.get
    contentAsString(action(request)) should equal("1Some(optional)Test2(custom)")
  }

  it should "produce a TypeException if FunctionInfo's types do not fit the supplied args" in {

    def fnc(s: String) = Action(Results.Ok(s))

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") :: Nil)

    val result = tools.actionService.execute('someId, info, Seq.empty).left.get

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

    val args = Seq(Arg("s0", "s0"), Arg("s1", "s1"), Arg("s2", "s2"), Arg("s3", "s3"), Arg("s4", "s4"),
      Arg("s5", "s5"), Arg("s6", "s6"), Arg("s7", "s7"), Arg("s8", "s8"), Arg("s9", "s9"), Arg("s10", "s10"))

    val result = tools.actionService.execute('someId, info, args).left.get

    result shouldBe a[TypeException]
    result.getMessage should equal("'someId: too many arguments: 11")
  }

  def actionService = tools.actionService.asInstanceOf[ActionBuilderImpl#ActionServiceImpl]

  "ActionService#values" should "extract the Arg values if the FunctionInfo.types align with the args" in {
    def fnc(s: String, d: Double) = s + d

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") ::("d", "scala.Double") :: Nil)
    val args = Seq(Arg("s", "hello"), Arg("d", 1d))

    actionService.values(info, args).right.value should equal(Seq("hello", 1d))
  }

  it should "extract the Arg values if there are more args then types" in {
    def fnc(s: String, d: Double) = s + d

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") ::("d", "scala.Double") :: Nil)
    val args = Seq(Arg("s", "hello"), Arg("d", 1d), Arg("i", 1))

    actionService.values(info, args).right.value should equal(Seq("hello", 1d))
  }

  it should "yield an ArgError if the types do not match" in {
    def fnc(s: String, d: Double) = s + d

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") ::("d", "scala.Int") :: Nil)
    val args = Seq(Arg("s", "hello"), Arg("d", 1d))

    actionService.values(info, args).left.value.msg should equal(
      "'d:scala.Int' not found in Arguments(s:java.lang.String,d:scala.Double)")
  }

  it should "yield an ArgError if the names do not match" in {
    def fnc(s: String, d: Double) = s + d

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") ::("i", "scala.Double") :: Nil)
    val args = Seq(Arg("s", "hello"), Arg("d", 1d))

    actionService.values(info, args).left.value.msg should equal(
      "'i:scala.Double' not found in Arguments(s:java.lang.String,d:scala.Double)"
    )
  }

  it should "yield an ArgError if args are missing" in {
    def fnc(s: String, d: Double) = s + d

    val info = FunctionInfo(fnc _, ("s", "java.lang.String") ::("d", "scala.Double") :: Nil)
    val args = Seq(Arg("s", "hello"))

    actionService.values(info, args).left.value.msg should equal(
      "'d:scala.Double' not found in Arguments(s:java.lang.String)"
    )
  }

  "ActionService#scalaClassNameFor" should "return the classname for Int" in {
    val name = actionService.scalaClassNameFor(1)
    name should equal("scala.Int")
  }

  it should "return the classname for String" in {
    val name = actionService.scalaClassNameFor("123")
    name should equal("java.lang.String")
  }

  it should "return the classname for Double" in {
    val name = actionService.scalaClassNameFor(1d)
    name should equal("scala.Double")
  }

  it should "return the classname for Float" in {
    val name = actionService.scalaClassNameFor(1f)
    name should equal("scala.Float")
  }

  it should "return the classname for Long" in {
    val name = actionService.scalaClassNameFor(1l)
    name should equal("scala.Long")
  }

  it should "return the classname for Short" in {
    val name = actionService.scalaClassNameFor(1.toShort)
    name should equal("scala.Short")
  }

  it should "return the classname for Byte" in {
    val name = actionService.scalaClassNameFor(1.toByte)
    name should equal("scala.Byte")
  }

  it should "return the classname for Boolean" in {
    val name = actionService.scalaClassNameFor(true)
    name should equal("scala.Boolean")
  }

  it should "return the classname for Char" in {
    val name = actionService.scalaClassNameFor('a')
    name should equal("scala.Char")
  }

  it should "return the classname for Symbol" in {
    val name = actionService.scalaClassNameFor('someSymbol)
    name should equal("scala.Symbol")
  }

  it should "return 'undefined' for any local class without a canonical name" in {
    case class Test(name: String)
    val name = actionService.scalaClassNameFor(Test("yo"))
    name should equal("undefined")
  }

  case class Test2(name: String)

  it should "return the classname for any custom class" in {
    val name = actionService.scalaClassNameFor(Test2("yo"))
    name should equal("org.splink.pagelets.ActionBuilderTest.Test2")
  }

  it should "return the Option classname Some[_]" in {
    val name = actionService.scalaClassNameFor(Option("yo"))
    name should equal("scala.Option")
  }

  it should "return the Option classname for None" in {
    val name = actionService.scalaClassNameFor(None)
    name should equal("scala.Option")
  }

  "ActionService#eitherSeq" should "convert the whole Seq if there are no Left" in {
    val xs = Seq(Right("One"), Right("Two"), Right("Three"))

    val result = actionService.eitherSeq(xs)
    result should equal(Right(Seq("One", "Two", "Three")))
  }

  it should "produce the last Left if the given Seq contains one" in {
    val xs = Seq(Right("One"), Left("Oops"), Left("Oops2"), Right("four"))

    val result = actionService.eitherSeq(xs)
    result should equal(Left("Oops2"))
  }

}
