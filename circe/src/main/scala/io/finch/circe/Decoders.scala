package io.finch.circe

import cats.syntax.show._
import com.twitter.finagle.netty3.ChannelBufferBuf
import com.twitter.util.{Return, Throw, Try}
import io.circe.Decoder
import io.circe.jawn._
import io.finch.{Decode, Error}
import io.finch.internal.BufText
import java.nio.charset.StandardCharsets

trait Decoders {

  /**
   * Maps a Circe's [[Decoder]] to Finch's [[Decode]].
   */
  implicit def decodeCirce[A: Decoder]: Decode.Json[A] = Decode.json { (b, cs) =>
    val attemptJson = cs match {
      case StandardCharsets.UTF_8 =>
        parseByteBuffer(ChannelBufferBuf.Owned.extract(b).toByteBuffer()).right.flatMap(_.as[A])
      case _ => decode[A](BufText.extract(b, cs))
    }
    attemptJson.fold[Try[A]](error => Throw[A](Error(error.show)), value => Return(value))
  }
}
