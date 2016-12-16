package io.finch

import com.twitter.finagle.http.{Request, Status}
import com.twitter.util.{Base64StringEncoder, Future}

class BasicAuthSpec extends FinchSpec {

  behavior of "BasicAuth"

  private[this] def encode(user: String, password: String) =
    "Basic " + Base64StringEncoder.encode(s"$user:$password".getBytes)

  private[this] def unauthorized(realm: String) =
    Unauthorized(BasicAuthFailed)
      .withHeader("WWW-Authenticate" -> s"""Basic realm="$realm"""")

  it should "has a proper string representation" in {
    check { (realm: String, s: String) =>
      BasicAuth(realm)((_, _) => Future.False)(s: Endpoint0).toString === s"""BasicAuth(realm="$realm", $s)"""
    }
  }

  it should "auth the endpoint" in {
    check { (c: BasicAuthCredentials, realm: String, req: Request) =>
      req.authorization = encode(c.user, c.pass)

      val e = BasicAuth(realm)((u, p) => Future(u == c.user && p == c.pass))(Endpoint.const("foo"))
      val i = Input.request(req)

      e(i).awaitOutputUnsafe() === Some(Ok("foo")) && {
        req.authorization = "secret"
        e(i).awaitOutputUnsafe() === Some(unauthorized(realm))
      }
    }
  }

  it should "reach the unprotected endpoint" in {
    val e = BasicAuth("realm")((_, _) => Future.False)("a") :+: ("b" :: Endpoint.const("foo"))

    val protectedInput = Input.get("/a")
    e(protectedInput).remainder shouldBe Some(protectedInput.drop(1))
    e(protectedInput).awaitOutputUnsafe() shouldBe Some(unauthorized("realm"))

    val unprotectedInput = Input.get("/b")
    e(unprotectedInput).remainder shouldBe Some(unprotectedInput.drop(1))
    e(unprotectedInput).awaitOutputUnsafe().map(_.status) shouldBe Some(Status.Ok)

    val notFound = Input.get("/c")
    e(notFound).remainder shouldBe None // 404

    val notFoundPartialMatch = Input.get("/a/b")
    e(notFoundPartialMatch).remainder shouldBe Some(notFoundPartialMatch.drop(1)) // 404
  }
}
