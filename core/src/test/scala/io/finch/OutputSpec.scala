package io.finch

import com.twitter.io.Buf
import com.twitter.finagle.http.Status
import shapeless.Witness

class OutputSpec extends FinchSpec {

  behavior of "Output"

  it should "propagate status to response" in {
    check { o: Output[String] => o.toResponse[Witness.`"text/plain"`.T]().status == o.status }
  }

  it should "propagate headers to response" in {
    check { (o: Output[String], headers: Headers) =>
      val rep = headers.m.foldLeft(o)((acc, h) => acc.withHeader(h._1 -> h._2)).toResponse[Witness.`"text/plain"`.T]()
      headers.m.forall(h => rep.headerMap(h._1) === h._2)
    }
  }

  it should "propagate cookies to response" in {
    check { (o: Output[String], cookies: Cookies) =>
      val rep = cookies.c.foldLeft(o)((acc, c) => acc.withCookie(c)).toResponse[Witness.`"text/plain"`.T]()
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
      (of: Output[Unit]).toResponse[Witness.`"text/plain"`.T]().content ===
        Encode.encodeExceptionAsTextPlain(of.cause)
    }
  }

  it should "propagate empytiness to response" in {
    check { of: Output.Empty =>
      (of: Output[Unit]).toResponse[Witness.`"text/plain"`.T]().content === Buf.Empty
    }
  }

  it should "propagate payload to response" in {
    check { op: Output.Payload[String] =>
      Some(op.toResponse[Witness.`"text/plain"`.T]().contentString) ===
        Buf.Utf8.unapply(Encode.encodeString(op.value))
    }
  }
}
