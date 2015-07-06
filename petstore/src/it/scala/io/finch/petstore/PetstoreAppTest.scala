package io.finch.petstore

import com.twitter.finagle.{Httpx, Service}
import com.twitter.finagle.httpx.RequestBuilder
import com.twitter.util.Await
import java.net.{InetSocketAddress, URL}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

class PetstoreAppTest extends FlatSpec with Matchers with BeforeAndAfter {
  var app: PetstoreApp = _
  var client: Service[HttpRequest, HttpResponse] = _

  before {
    app = new PetstoreApp()
    client = Httpx.newService(s"127.0.0.1:8080")
  }

  after {
    app.close()
    client.close()
  }

  "The petstore app" should "return valid pets" in {
    val request = RequestBuilder().url(new URL(s"http://127.0.0.1:8080/pet/1")).buildGet

    
    val result = Await.result(client(request))

    result.size shouldBe app.count

    result.zipWithIndex.foreach {
      case (response, i) =>
        response.statusCode shouldBe 201
        response.location shouldBe Some(s"/users/${ i + app.count }")
    }
  }
}
