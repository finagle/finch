package io.finch

import cats.effect.IO
import com.twitter.concurrent.AsyncStream
import com.twitter.io.Buf
import com.twitter.util.Await
import io.finch.streaming.StreamFromReader.AsyncStreamF

class StreamingSpec extends FinchSpec {

  checkAll("AsyncStream.streamBody", StreamingLaws[AsyncStreamF, IO](
    AsyncStream.fromSeq,
    stream => Await.result(stream.toSeq).toList
  ).all)

}
