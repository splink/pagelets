package org.splink.raven

import org.scalatest.{FlatSpec, Matchers}

case class Name(s: String)
case class Age(i: Int)
case class Person(name: Name, age: Option[Age])

class SerializerTest extends FlatSpec with Matchers {

  val serializer = new SerializerImpl {}.serializer

  "Serializer" should "serialize and deserialize a Serializable" in {
    val serialized = serializer.serialize("123").right.get
    val result = serializer.deserialize[String](serialized).right.get
    result should equal("123")
  }

  it should "serialize and deserialize a complex Serializable" in {
    val max = Person(Name("Max"), age = None)
    val serialized = serializer.serialize(max).right.get

    val result = serializer.deserialize[Person](serialized).right.get
    result should equal(max)
  }

  it should "fail gracefully when fed with a path dependent type" in {
    case class Fail(n: String)

    val serialized = serializer.serialize(Fail("fail"))
    serialized.isLeft should be(true)
  }

  it should "fail gracefully when it can't deserialize the given String" in {
    val deserialized = serializer.deserialize[Serializable]("some gibberish")
    deserialized.isLeft should be(true)
  }

  it should "fail gracefully when it the given deserialization type is wrong" in {
    val deserialized = serializer.deserialize[Person]("rO0ABXQAAzEyMw==")
    deserialized.isLeft should be(true)
  }

}
