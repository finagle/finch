package io.finch.json4s

import com.twitter.finagle.httpx.Request
import com.twitter.io.Buf.Utf8
import com.twitter.util.{Await, Future, Return}
import io.finch.request.RequestReader
import io.finch.request._
import io.finch.response._
import org.json4s.DefaultFormats
import org.json4s.ext.JodaTimeSerializers
import org.scalatest.{Matchers, FlatSpec}

class Json4sSpec extends FlatSpec with Matchers{

  implicit val formats = DefaultFormats ++ JodaTimeSerializers.all

  private def bar = Bar(1, true)
  private def barJson = """{"x":1,"y":true}"""

  "Json4sEncode" should "encode a case class into JSON" in {
    encodeJson[Bar].apply(bar) shouldBe barJson
  }

  it should "decode a case class from JSON" in {
    decodeJson[Bar].apply(barJson) shouldBe Return(bar)
  }

  it should "fail given invalid JSON" in {
    val invalidJson = """{"x": {, "y": true}"""
    decodeJson[Bar].apply(invalidJson).isThrow shouldBe true
  }

  it should "work w/o exceptions with ResponseBuilder" in {
    Ok(bar).getContentString() shouldBe barJson
  }

  it should "work with higher kinded types" in {
    val list = List(1, 2, 3)
    val encode = encodeJson[List[Int]]
    val decode = decodeJson[List[Int]]

    encode(list) shouldBe "[1,2,3]"
    decode("[1,2,3]") shouldBe Return(List(1, 2, 3))
  }

  it should "work w/o exceptions with RequestReader" in {
    val b = Utf8(barJson)
    val req = Request()
    req.setContentTypeJson()
    req.contentLength = b.length
    req.content = b

    val rFoo: RequestReader[Bar] = body.as[Bar]
    val foo: Future[Bar] = body.as[Bar].apply(req)
    val roFoo: RequestReader[Option[Bar]] = bodyOption.as[Bar]
    val oFoo: Future[Option[Bar]] = bodyOption.as[Bar].apply(req)

    Await.result(rFoo(req)) shouldBe bar
    Await.result(foo) shouldBe bar
    Await.result(roFoo(req)) shouldBe Some(bar)
    Await.result(oFoo) shouldBe Some(bar)
  }
}
