package io.finch

import com.twitter.finagle.http.Request
import com.twitter.util.Base64StringEncoder

class BasicAuthSpec extends FinchSpec {

  behavior of "BasicAuth"

  private[this] def encode(user: String, password: String) =
    "Basic " + Base64StringEncoder.encode(s"$user:$password".getBytes)

  it should "has a proper string representation" in {
    check { s: String =>
      BasicAuth("foo", "bar")(s: Endpoint0).toString === s"BasicAuth($s)"
    }
  }

  it should "auth the endpoint" in {
    check { (u: String, p: String, req: Request) =>
      req.authorization = encode(u, p)

      val e = BasicAuth(u, p)(Endpoint(Ok("foo")))
      val i = Input(req)

      e(i).output === Some(Ok("foo")) && {
        req.authorization = "secret"
        e(i).output === Some(Unauthorized(BasicAuthFailed))
      }
    }
  }
}
