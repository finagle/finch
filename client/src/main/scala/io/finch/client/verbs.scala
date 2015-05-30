package io.finch
package client

import cats.data.Xor
import com.twitter.finagle.httpx._
import com.twitter.finagle.{Httpx, Service}
import com.twitter.util.{Future, Promise}

sealed trait Verb extends Service[FinchRequest, Result] {

  def apply(fReq: FinchRequest): Future[Result] = {
     val req = RequestBuilder()
       .url("https://" + fReq.conn.host + fReq.path)
       .addHeader("Host", fReq.conn.host) 
       .addHeader("User-Agent", "finch/0.7.0")
       .build(httpVerb(), None)
  

    val result = Promise[Result]
    fReq.conn(req) onSuccess { resp =>
      result.setValue(Xor.Right(new Resource(resp.content)))
    } onFailure { ex =>
      result.setValue(Xor.Left(ex.toString))
    }
    result
  }

  private[this] def httpVerb(): Method = this match {
    case Get => Method.Get
  }
}

case object Get extends Verb

case class Connection(host: String, port: Int = 443) extends Service[Request, Response] {
  val client = new Httpx.Client()
    .withTlsWithoutValidation()
    .newService(host + ":" + port.toString, host)

  def apply(req: Request): Future[Response] = client(req); client.close()
}
