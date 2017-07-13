package io.finch.circe

import com.twitter.io.Buf
import com.twitter.util.Future
import io.catbird.util._
import io.circe.Decoder
import io.circe.streaming.{byteParser, decoder}
import io.iteratee.Enumeratee

trait Streaming {
  implicit def decoderEnumeratee[A : Decoder]: Enumeratee[Future, Buf, A] =
    Enumeratee
      .map[Future, Buf, Array[Byte]](Buf.ByteArray.Owned.extract)
      .andThen(byteParser[Future])
      .andThen(decoder[Future, A])
}
