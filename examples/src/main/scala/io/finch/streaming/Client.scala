package io.finch.streaming

import com.twitter.finagle.Http
import com.twitter.finagle.http.{Method, Request}
import com.twitter.util.Await

import scala.util.Random

object Client extends App {

  val client = Http.client.withStreaming(enabled = true).newService("127.0.0.1:8082")

  val randomString = Random.alphanumeric.take(32).mkString
  val request = Request(Method.Post, "/upload")

  request.setContentString(randomString)

  Await.result(client(request))
}
