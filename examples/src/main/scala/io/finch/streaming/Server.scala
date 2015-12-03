package io.finch.streaming

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.Http
import com.twitter.io.Buf
import com.twitter.io.Buf.ByteArray
import com.twitter.server.TwitterServer
import com.twitter.util.{JavaTimer, Await, Future}
import io.finch._
import com.twitter.conversions.time._

object Server extends TwitterServer {

  implicit val timer = new JavaTimer

  //read from request body with chunk size equal to 8 bytes
  val requestReader: RequestReader[AsyncStream[Option[Buf]]] = body.async(8)

  //for each connection create stream of defined size
  def asyncStream(size: Int): AsyncStream[Option[Int]] = {
    Some(scala.util.Random.nextInt(100)) +::
      AsyncStream.fromFuture(Future.sleep(500.millis)).flatMap(_ => {
        if (size > 0) asyncStream(size - 1) else AsyncStream.of(None)
      })
  }

  val streaming: Endpoint[AsyncStream[Option[Buf]]] = get("stream" / int) { size: Int =>
    Ok(asyncStream(size).map { int =>
      int.map(Buf.Utf8 apply _.toString)
    })
  }

  val upload: Endpoint[String] = post("upload" ? requestReader) { stream: AsyncStream[Option[Buf]]  =>
    stream.foldLeft("") { (data, buf) =>
      val chunk = buf.flatMap(Buf.Utf8.unapply)
      log.info(s"Total data: $data")
      log.info(s"Current buffer: $chunk")
      data + chunk.getOrElse("")
    } map { uploaded =>
      log.info(s"Final data: $uploaded")
      Ok(uploaded)
    }
  }

  //just to prove that simple endpoints could exists with streaming one
  val helloWorld: Endpoint[String] = get("hello") {
    Ok("hello world")
  }

  val service = (streaming :+: helloWorld :+: upload).toService

  def main(): Unit = {
    log.info("Starting streaming service")

    val server = Http.server.withStreaming(enabled = true).serve(":8082", service)
    onExit {
      server.close()
    }
    Await.ready(server)
  }

}
