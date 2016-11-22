package io.finch.circe

import com.twitter.util.{Return, Throw, Try}
import io.circe.Decoder
import io.circe.jawn._
import io.finch.Decode
import io.finch.internal.{BufText, HttpContent}
import java.nio.charset.StandardCharsets

trait Decoders {

  /**
   * Maps a Circe's [[Decoder]] to Finch's [[Decode]].
   */
  implicit def decodeCirce[A: Decoder]: Decode.Json[A] = Decode.json { (b, cs) =>
    val attemptJson = cs match {
      case StandardCharsets.UTF_8 =>
        parseByteBuffer(b.asByteBuffer).right.flatMap(_.as[A])
      case _ => decode[A](BufText.extract(b, cs))
    }

    attemptJson.fold[Try[A]](Throw.apply, Return.apply)
  }
}
