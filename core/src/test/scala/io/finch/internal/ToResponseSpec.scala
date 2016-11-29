package io.finch.internal

import com.twitter.concurrent.AsyncStream
import com.twitter.io.Buf
import com.twitter.util.Await
import io.finch.FinchSpec
import java.nio.charset.StandardCharsets

class ToResponseSpec extends FinchSpec {
  "ToResponse" should "pick correct instance for AsyncStream[Buf]" in {
    import io.finch._

    val tr: ToResponse.Aux[AsyncStream[Buf], Text.Plain] = implicitly

    check { (chunks: List[Buf]) =>
      val in = AsyncStream.fromSeq(chunks)
      val out = AsyncStream.fromReader(tr(in, StandardCharsets.UTF_8).reader)
      Await.result(in.toSeq) === Await.result(out.toSeq)
    }
  }
}
