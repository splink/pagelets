package org.splink.pagelets

import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec

class RequestIdTest extends AnyFlatSpec with Matchers {
  "RequestId.create" should "create a 6 char request id wrapped in brackets" in {
    RequestId.create.id should (startWith ("[") and endWith ("]") and have length 8)
  }

  "RequestId.toString" should "return the same as RequestId.id" in {
    val requestId = RequestId.create
    requestId.id should equal(requestId.toString)
  }

}
