package io.finch.internal

import com.twitter.concurrent.AsyncStream
import com.twitter.io.Buf
import com.twitter.io.Charsets
import com.twitter.util.Await
import io.finch.FinchSpec

class ToResponseSpec extends FinchSpec {
  "ToResponse" should "pick correct instance for AsyncStream[Buf]" in {
    import io.finch._

    val tr: ToResponse.Aux[AsyncStream[Buf], Text.Plain] = implicitly

    check { (chunks: List[Buf]) =>
      val in = AsyncStream.fromSeq(chunks)
      val out = AsyncStream.fromReader(tr(in, Charsets.Utf8).reader)
      Await.result(in.toSeq) === Await.result(out.toSeq)
    }
  }
}
