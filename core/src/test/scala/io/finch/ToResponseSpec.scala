package io.finch

import com.twitter.concurrent.AsyncStream
import com.twitter.io.{Buf, Reader}
import com.twitter.util.Await
import java.nio.charset.StandardCharsets

class ToResponseSpec extends FinchSpec {

  behavior of "ToResponse"

  it should "pick correct instance for AsyncStream[Buf]" in {
    val tr: ToResponse.Aux[AsyncStream[Buf], Text.Plain] = implicitly

    check { chunks: List[Buf] =>
      val in = AsyncStream.fromSeq(chunks)
      val out = Reader.toAsyncStream(tr(in, StandardCharsets.UTF_8).reader)
      Await.result(in.toSeq) === Await.result(out.toSeq)
    }
  }
}
