package io.finch

import com.twitter.finagle.http.{Response, Cookie}
import com.twitter.io.Buf
import io.finch.response.EncodeResponse

class OutputSpec extends FinchSpec {

  "Output" should "propagate status to response" in {
    check { o: Output[Response] => o.toResponse.status == o.status }
  }

  it should "propagate charset to response" in {
    check { (o: Output[Response], cs: OptionalNonEmptyString) =>
      val rep = o.withCharset(cs.o).toResponse
      rep.charset === cs.o.orElse(EncodeResponse.encodeMap.charset)
    }
  }

  it should "propagate contentType to response" in {
    check { (o: Output[Response], ct: OptionalNonEmptyString) =>
      val rep = o.withContentType(ct.o).toResponse
      rep.contentType.forall(_.startsWith(ct.o.getOrElse(EncodeResponse.encodeMap.contentType)))
    }
  }

  it should "propagate headers to response" in {
    check { (o: Output[Response], headers: Headers) =>
      val rep = headers.m.foldLeft(o)((acc, h) => acc.withHeader(h._1 -> h._2)).toResponse
      headers.m.forall(h => rep.headerMap(h._1) === h._2)
    }
  }

  it should "propagate cookies to response" in {
    check { (o: Output[Response], cookies: Cookies) =>
      val rep = cookies.c.foldLeft(o)((acc, c) => acc.withCookie(c)).toResponse
      cookies.c.forall(c => rep.cookies(c.name) === c)
    }
  }

  "Failure" should "propagate error message to response" in {
    check { of: Output.Failure =>
      Some(of.toResponse.contentString) === Buf.Utf8.unapply(EncodeResponse.encodeMap(of.message))
    }
  }

  "Payload" should "propagate payload to response" in {
    check { op: Output.Payload[String] =>
      Some(op.map(a => ToService.encodeResponse(a)).toResponse.contentString) ===
        Buf.Utf8.unapply(EncodeResponse.encodeString(op.value))
    }
  }
}
