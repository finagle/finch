package io.finch.wrk

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Method, Request, Response, Status}
import com.twitter.io.Buf
import com.twitter.util.Future

/**
 * How to benchmark this:
 *
 * 1. Run the server: sbt 'examples/runMain io.finch.wrk.Finagle'
 * 2. Run wrk: wrk -t4 -c24 -d30s -s examples/src/main/scala/io/finch/wrk/wrk.lua http://localhost:8081/
 *
 * Rule of thumb for picking values for params `t` and `c` (given that `n` is a number of logical
 * cores your machine has, including HT):
 *
 *   t = n
 *   c = t * n * 1.5
 */
object Finagle extends App {

  val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  val roundTrip: Service[Request, Response] = new Service[Request, Response] {
    def apply(req: Request): Future[Response] =
      if (req.method != Method.Post) Future.value(Response(req.version, Status.NotFound))
      else {
        val payloadIn = mapper.readValue(req.contentString, classOf[Payload])
        val payloadOut = mapper.writeValueAsString(payloadIn)

        val rep = Response(req.version, Status.Ok)
        rep.content =  Buf.Utf8(payloadOut)
        rep.contentType = "application/json"

        Future.value(rep)
      }
  }

  serve(roundTrip)
}
