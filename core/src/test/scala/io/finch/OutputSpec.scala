package io.finch

import com.twitter.io.Buf
import io.finch.response.EncodeResponse

class OutputSpec extends FinchSpec {

  "Output" should "propagate status to response" in {
    check { o: Output[String] => o.toResponse().status == o.status }
  }

  it should "propagate charset to response" in {
    check { (o: Output[String], cs: OptionalNonEmptyString) =>
      val rep = o.withCharset(cs.o).toResponse()
      rep.charset === cs.o.orElse(EncodeResponse.encodeString.charset)
    }
  }

  it should "propagate contentType to response" in {
    check { (o: Output[String], ct: OptionalNonEmptyString) =>
      val rep = o.withContentType(ct.o).toResponse()
      rep.contentType.forall(_.startsWith(ct.o.getOrElse(EncodeResponse.encodeString.contentType)))
    }
  }

  it should "propagate headers to response" in {
    check { (o: Output[String], headers: Headers) =>
      val rep = headers.m.foldLeft(o)((acc, h) => acc.withHeader(h._1 -> h._2)).toResponse()
      headers.m.forall(h => rep.headerMap(h._1) === h._2)
    }
  }

  it should "propagate cookies to response" in {
    check { (o: Output[String], cookies: Cookies) =>
      val rep = cookies.c.foldLeft(o)((acc, c) => acc.withCookie(c)).toResponse()
      cookies.c.forall(c => rep.cookies(c.name) === c)
    }
  }

  "Failure" should "propagate content to response" in {
    check { (of: Output.Failure, s: String) =>
      implicit val ee: EncodeResponse[Exception] = EncodeResponse.fromString("application/json") { _ => s }
      Some(of.toResponse().contentString) === Buf.Utf8.unapply(ee(of.cause))
    }
  }

  "Payload" should "propagate payload to response" in {
    check { op: Output.Payload[String] =>
      Some(op.toResponse().contentString) === Buf.Utf8.unapply(EncodeResponse.encodeString(op.value))
    }
  }
}
