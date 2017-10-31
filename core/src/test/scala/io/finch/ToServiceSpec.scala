package io.finch

import com.twitter.finagle.http.Response
import com.twitter.util.Await

class ToServiceSpec extends FinchSpec {

  behavior of "ToService"

  it should "set FinchContext.PathField on the Request.ctx and the Response.ctx" in {
    val passingEndpoint: Endpoint[Response] = get(/).map(_ => Response())
    val service = passingEndpoint.toServiceAs[Text.Plain]

    val request = Input.get("/").request
    val response = Await.result(service(request))

    request.ctx[String](FinchContext.RequestPathField) shouldBe passingEndpoint.toString
    response.ctx(FinchContext.ResponsePathField) shouldBe passingEndpoint.toString
  }

  it should "set FinchContext.PathField correctly with multiple" in {
    val passingEndpoint: Endpoint[Response] =
      get(/).map(_ => Response()) coproduct
      get(/ :: path[String]).map(_ => Response())
    val service = passingEndpoint.toServiceAs[Text.Plain]

    val request = Input.get("/").request
    val response = Await.result(service(request))

    request.ctx[String](FinchContext.RequestPathField) shouldBe "GET /"
    response.ctx(FinchContext.ResponsePathField) shouldBe "GET /"
  }
}
