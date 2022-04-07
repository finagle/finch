package io.finch.internal

import java.nio.charset.{Charset, StandardCharsets}

import com.twitter.finagle.http.Request
import io.finch.FinchSpec

class HttpMessageSpec extends FinchSpec {

  def slowCharset(req: Request): Charset = req.charset match {
    case Some(cs) => Charset.forName(cs)
    case None     => StandardCharsets.UTF_8
  }

  behavior of "HttpMessage"

  it should "charsetOrUtf8" in {
    check { cs: Charset =>
      val req = Request()
      req.contentType = "application/json"
      req.charset = cs.displayName()

      req.charsetOrUtf8 === slowCharset(req)
    }

    check { cs: Charset =>
      val req = Request()
      req.contentType = "application/json;   charset=" + cs.displayName()

      req.charsetOrUtf8 === slowCharset(req)
    }

    assert(Request().charsetOrUtf8 == StandardCharsets.UTF_8)
  }

  it should "mediaTypeOrEmpty" in {
    check { cs: Option[Charset] =>
      val req = Request()
      req.contentType = "application/json"
      cs.foreach(c => req.charset = c.displayName())

      req.mediaTypeOrEmpty === "application/json"
    }
  }
}
