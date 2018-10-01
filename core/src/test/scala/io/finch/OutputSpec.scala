package io.finch

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.http.Status
import com.twitter.io.Buf
import java.nio.charset.{Charset, StandardCharsets}
import scala.util.{Failure, Success, Try}

class OutputSpec extends FinchSpec {

  behavior of "Output"

  it should "propagate status to response" in {
    check { o: Output[String] => o.toResponse[Text.Plain].status == o.status }
  }

  it should "propagate overridden status to response" in {
    check { (o: Output[String], s: Status) =>
      o.withStatus(s).toResponse[Text.Plain].status === s
    }
  }

  it should "propagate charset to response" in {
    check { (o: Output[String], cs: Charset) =>
      val rep = o.withCharset(cs).toResponse[Text.Plain]
      (rep.content.isEmpty && !rep.isChunked) || Some(cs.displayName.toLowerCase) === rep.charset
    }
  }

  it should "propagate charset to chunked response" in {
    check { (o: Output[AsyncStream[String]], cs: Charset) =>
      val rep = o.withCharset(cs).toResponse[Text.Plain]
      (rep.content.isEmpty && !rep.isChunked) || Some(cs.displayName.toLowerCase) === rep.charset
    }
  }



  it should "propagate headers to response" in {
    check { (o: Output[String], headers: Headers) =>
      val rep = headers.m.foldLeft(o)((acc, h) => acc.withHeader(h._1 -> h._2)).toResponse[Text.Plain]
      headers.m.forall(h => rep.headerMap(h._1) === h._2)
    }
  }

  it should "propagate cookies to response" in {
    check { (o: Output[String], cookies: Cookies) =>
      val rep = cookies.c.foldLeft(o)((acc, c) => acc.withCookie(c)).toResponse[Text.Plain]
      cookies.c.forall(c => rep.cookies(c.name) === c)
    }
  }

  it should "set the corresponding status while predefined smart constructors are used" in {
    val cause = new Exception
    Ok(()).status shouldBe Status.Ok
    Created(()).status shouldBe Status.Created
    Accepted.status shouldBe Status.Accepted
    NoContent.status shouldBe Status.NoContent
    BadRequest(cause).status shouldBe Status.BadRequest
    Unauthorized(cause).status shouldBe Status.Unauthorized
    PaymentRequired(cause).status shouldBe Status.PaymentRequired
    Forbidden(cause).status shouldBe Status.Forbidden
    NotFound(cause).status shouldBe Status.NotFound
    MethodNotAllowed(cause).status shouldBe Status.MethodNotAllowed
    NotAcceptable(cause).status shouldBe Status.NotAcceptable
    RequestTimeout(cause).status shouldBe Status.RequestTimeout
    Conflict(cause).status shouldBe Status.Conflict
    Gone(cause).status shouldBe Status.Gone
    LengthRequired(cause).status shouldBe Status.LengthRequired
    PreconditionFailed(cause).status shouldBe Status.PreconditionFailed
    RequestEntityTooLarge(cause).status shouldBe Status.RequestEntityTooLarge
    RequestedRangeNotSatisfiable(cause).status shouldBe Status.RequestedRangeNotSatisfiable
    EnhanceYourCalm(cause).status shouldBe Status.EnhanceYourCalm
    UnprocessableEntity(cause).status shouldBe Status.UnprocessableEntity
    TooManyRequests(cause).status shouldBe Status.TooManyRequests
    InternalServerError(cause).status shouldBe Status.InternalServerError
    NotImplemented(cause).status shouldBe Status.NotImplemented
    BadGateway(cause).status shouldBe Status.BadGateway
    ServiceUnavailable(cause).status shouldBe Status.ServiceUnavailable
    GatewayTimeout(cause).status shouldBe Status.GatewayTimeout
    InsufficientStorage(cause).status shouldBe Status.InsufficientStorage
  }

  it should "propagate cause to response" in {
    check { of: Output.Failure =>
      (of: Output[Unit]).toResponse[Text.Plain].content ===
        Encode[Exception, Text.Plain].apply(of.cause, of.charset.getOrElse(StandardCharsets.UTF_8))
    }
  }

  it should "propagate empytiness to response" in {
    check { of: Output.Empty =>
      (of: Output[Unit]).toResponse[Text.Plain].content === Buf.Empty
    }
  }

  it should "propagate payload to response" in {
    check { op: Output.Payload[String] =>
      op.toResponse[Text.Plain].content ===
        Encode[String, Text.Plain].apply(op.value, op.charset.getOrElse(StandardCharsets.UTF_8))
    }
  }

  it should "create an empty endpoint with given status when calling unit" in {
    check { s: Status =>
      Output.unit(s).toResponse[Text.Plain].status === s
    }
  }

  it should "throw an exception on calling value on an Empty output" in {
    check { e: Output.Empty =>
      Try(e.value) match {
        case Failure(f) => f.getMessage === "empty output"
        case _ => false
      }
    }
  }

  it should "throw an exception on calling value on a Failure output" in {
    check { f: Output.Failure =>
      Try(f.value) match {
        case Failure(ex) => ex.getMessage === f.cause.getMessage
        case _ => false
      }
    }
  }

  it should "check equivalences of arbitrary string outputs" in {
    check { (oa: Output[String], ob: Output[String]) =>
      Output.outputEq[String].eqv(oa, ob) === (oa == ob)
    }
  }

  it should "check equivalences of empty outputs" in {
    check { (ea: Output.Empty, eb: Output.Empty) =>
      Output.outputEq[String].eqv(ea, eb) === (ea == eb)
    }
  }

  it should "check equivalences of failure outputs" in {
    check { (fa: Output.Failure, fb: Output.Failure) =>
      Output.outputEq[String].eqv(fa, fb) === (fa == fb)
    }
  }

  it should "traverse arbitrary outputs" in {
    check { oa: Output[String] =>
      oa.traverse[Try, String](_ => Success(oa.value)) === Success(oa)
    }
  }
}
