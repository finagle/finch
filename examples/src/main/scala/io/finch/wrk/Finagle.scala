package io.finch.wrk

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.io.Buf
import com.twitter.util.Future
import io.finch.internal._

/** How to benchmark this:
  *
  *   1. Run the server: sbt 'examples/runMain io.finch.wrk.Finagle' 2. Run wrk: wrk -t4 -c24 -d30s http://localhost:8081/
  *
  * Rule of thumb for picking values for params `t` and `c` (given that `n` is a number of logical cores your machine has, including HT):
  *
  * t = n c = t * n * 1.5
  */
object Finagle extends Wrk {

  val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  serve(new Service[Request, Response] {
    def apply(req: Request): Future[Response] = {
      val payload = mapper.writeValueAsBytes(Payload("Hello, World!"))

      val rep = Response(req.version, Status.Ok)
      rep.content = Buf.ByteArray.Owned(payload)
      rep.contentType = "application/json"
      rep.date = currentTime()
      rep.server = "Finagle"

      Future.value(rep)
    }
  })
}
