package io.finch
package client

import com.twitter.finagle.httpx._
import com.twitter.finagle.{Httpx, Service}
import com.twitter.io.Buf._
import com.twitter.util.Future

sealed trait Verb extends Service[FinchRequest, Resource] {

  def apply(fReq: FinchRequest): Future[Resource] = {
     val req = RequestBuilder()
       .url("https://" + fReq.conn.host + fReq.path)
       .addHeader("Host", fReq.conn.host) 
       .addHeader("User-Agent", "finch/0.7.0")
       .build(httpVerb(), None)
  
     fReq.conn(req) map { r =>
        val body = Utf8.unapply(r.content) match {
          case Some(x) => x
          case None    => ""
        }
        new Resource(body)
      }
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

  def apply(req: Request): Future[Response] = client(req)
}
