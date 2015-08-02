package io.finch.request

import com.twitter.finagle.httpx.Request
import com.twitter.io.Buf.ByteArray
import com.twitter.util.{Await, Future, Try}
import org.scalatest.{FlatSpec, Matchers}
import items._

class BodySpec extends FlatSpec with Matchers {
  val foo = "foo"
  val fooBytes = foo.getBytes("UTF-8")

  "A RequiredArrayBody" should "be properly read if it exists" in {
    val request: Request = requestWithBody(fooBytes)
    val futureResult: Future[Array[Byte]] = binaryBody(request)
    Await.result(futureResult) shouldBe fooBytes
  }

  it should "produce an error if the body is empty" in {
    val request: Request = requestWithBody(Array[Byte]())
    val futureResult: Future[Array[Byte]] = binaryBody(request)
    a [NotPresent] shouldBe thrownBy(Await.result(futureResult))
  }

  it should "have a corresponding RequestItem" in {
    binaryBody.item shouldBe BodyItem
  }

  "An OptionalArrayBody" should "be properly read if it exists" in {
    val request: Request = requestWithBody(fooBytes)
    val futureResult: Future[Option[Array[Byte]]] = binaryBodyOption(request)
    Await.result(futureResult).get shouldBe fooBytes
  }

  it should "produce an error if the body is empty" in {
    val request: Request = requestWithBody(Array[Byte]())
    val futureResult: Future[Option[Array[Byte]]] = binaryBodyOption(request)
    Await.result(futureResult) shouldBe None
  }

  it should "have a corresponding RequestItem" in {
    binaryBodyOption.item shouldBe BodyItem
  }

  "A RequiredStringBody" should "be properly read if it exists" in {
    val request: Request = requestWithBody(foo)
    val futureResult: Future[String] = body(request)
    Await.result(futureResult) shouldBe foo
  }

  it should "produce an error if the body is empty" in {
    val request: Request = requestWithBody("")
    val futureResult: Future[String] = body(request)
    a [NotPresent] shouldBe thrownBy(Await.result(futureResult))
  }

  "An OptionalStringBody" should "be properly read if it exists" in {
    val request: Request = requestWithBody(foo)
    val futureResult: Future[Option[String]] = bodyOption(request)
    Await.result(futureResult) shouldBe Some(foo)
  }

  it should "produce an error if the body is empty" in {
    val request: Request = requestWithBody("")
    val futureResult: Future[Option[String]] = bodyOption(request)
    Await.result(futureResult) shouldBe None
  }

  "RequiredArrayBody Reader" should "work without parentheses at call site" in {
    val reader = for {
      body <- binaryBody
    } yield body

    val request: Request = requestWithBody(fooBytes)
    Await.result(reader(request)) shouldBe fooBytes
  }

  "RequiredBody and OptionalBody" should "work with no request type available" in {
    implicit val decodeInt = new DecodeRequest[Int] {
       def apply(req: String): Try[Int] = Try(req.toInt)
    }
    val req = requestWithBody("123")
    val ri: RequestReader[Int] = body.as[Int]
    val i: Future[Int] = body.as[Int].apply(req)
    val oi: RequestReader[Option[Int]] = bodyOption.as[Int]
    val o = bodyOption.as[Int].apply(req)

    Await.result(ri(req)) shouldBe 123
    Await.result(i) shouldBe 123
    Await.result(oi(req)) shouldBe Some(123)
    Await.result(o) shouldBe Some(123)
  }

  it should "work with custom request and its implicit view to Request" in {
    implicit val decodeDouble = new DecodeRequest[Double] { // custom encoder
      def apply(req: String): Try[Double] = Try(req.toDouble)
    }
    case class CReq(http: Request) // custom request
    implicit val cReqEv = (req: CReq) => req.http // implicit view

    val req = CReq(requestWithBody("42.0"))
    val rd: RequestReader[Double] = body.as[Double]
    val d = body.as[Double].apply(req)
    val od: RequestReader[Option[Double]] = bodyOption.as[Double]
    val o: Future[Option[Double]] = bodyOption.as[Double].apply(req)

    Await.result(rd(req)) shouldBe 42.0
    Await.result(d) shouldBe 42.0
    Await.result(od(req)) shouldBe Some(42.0)
    Await.result(o) shouldBe Some(42.0)
  }
  
  it should "fail if the decoding of the body fails" in {
    implicit val decodeInt = new DecodeRequest[Int] {
       def apply(req: String): Try[Int] = Try(req.toInt)
    }
    val req = requestWithBody("foo")
    val ri: RequestReader[Int] = body.as[Int]
    val i: Future[Int] = body.as[Int].apply(req)
    val oi: RequestReader[Option[Int]] = bodyOption.as[Int]
    val o: Future[Option[Int]] = bodyOption.as[Int].apply(req)

    a [NotParsed] shouldBe thrownBy(Await.result(ri(req)))
    a [NotParsed] shouldBe thrownBy(Await.result(i))
    a [NotParsed] shouldBe thrownBy(Await.result(oi(req)))
    a [NotParsed] shouldBe thrownBy(Await.result(o))
  }

  private[this] def requestWithBody(body: String): Request = {
    requestWithBody(body.getBytes("UTF-8"))
  }

  private[this] def requestWithBody(body: Array[Byte]): Request = {
    val r = Request()
    r.content = ByteArray.Owned(body)
    r.contentLength = body.length.toLong
    r
  }
}
