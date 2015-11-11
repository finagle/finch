package io.finch

import com.twitter.finagle.http.Cookie
import com.twitter.io.Buf
import io.finch.response.EncodeResponse

class OutputSpec extends FinchSpec {

  "Output" should "propagate status to response" in {
    check { of: Output[String] => of.map(a => ToService.encodeResponse(a)).toResponse.status == of.status }
  }

  it should "propagate charset to response" in {
    check { (of: Output[String], cs: OptionalNonEmptyString) =>
      val rep = of.withCharset(cs.o).map(a => ToService.encodeResponse(a)).toResponse
      rep.charset === cs.o.orElse(EncodeResponse.encodeMap.charset)
    }
  }

  it should "propagate contentType to response" in {
    check { (of: Output[String], ct: OptionalNonEmptyString) =>
      val rep = of.withContentType(ct.o).map(a => ToService.encodeResponse(a)).toResponse
      rep.contentType.forall(_.startsWith(ct.o.getOrElse(EncodeResponse.encodeMap.contentType)))
    }
  }

  ignore should "propagate headers to response" in {
    check { (o: Output[String], headers: Set[Header]) =>
      val rep = headers.foldLeft(o)((acc, h) => acc.withHeader(h.k -> h.v))
                       .map(a => ToService.encodeResponse(a)).toResponse
      headers.forall(h => rep.headerMap(h.k) === h.v)
    }
  }

  ignore should "propagate cookies to response" in {
    check { (o: Output[String], cookies: Set[Cookie]) =>
      val rep = cookies.foldLeft(o)((acc, c) => acc.withCookie(c))
                       .map(a => ToService.encodeResponse(a)).toResponse
      cookies.forall(c => rep.cookies(c.name) === c)
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
