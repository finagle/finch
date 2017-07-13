package io.finch.streaming

import com.twitter.finagle.http.Request
import com.twitter.io.Buf
import com.twitter.util.Await
import io.catbird.util._
import io.finch.Input
import org.scalatest.{FlatSpec, Matchers}

class EndpointStreamingSpec extends FlatSpec with Matchers {

  "asyncBufBody" should "interpret chunked request as Enumerator[Future, Buf]" in {
    val req = Request()
    req.setChunked(chunked = true)
    val writer = req.writer

    val vector = Vector("foo", "bar")

    def write(vector: Vector[String]): Unit = {
      if (vector.isEmpty) {
        writer.close()
      } else {
        writer.write(Buf.Utf8(vector.head)).foreach(_ => write(vector.tail))
      }
    }
    write(vector)

    val enumerator = asyncBufBody(Input(req, Seq.empty)).awaitValueUnsafe().get

    Await.result(enumerator.map(Buf.Utf8.unapply).map(_.get).toVector) shouldBe vector
  }

}
