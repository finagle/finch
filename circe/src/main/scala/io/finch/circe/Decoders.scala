package io.finch.circe

import com.twitter.util.{Future, Return, Throw, Try}
import io.catbird.util._
import io.circe.Decoder
import io.circe.jawn._
import io.circe.streaming._
import io.finch.{Application, Decode}
import io.finch.internal.HttpContent
import io.finch.iteratee.Enumerate
import java.nio.charset.StandardCharsets


trait Decoders {

  /**
   * Maps a Circe's [[Decoder]] to Finch's [[Decode]].
   */
  implicit def decodeCirce[A: Decoder]: Decode.Json[A] = Decode.json { (b, cs) =>
    val attemptJson = cs match {
      case StandardCharsets.UTF_8 => decodeByteBuffer[A](b.asByteBuffer)
      case _ => decode[A](b.asString(cs))
    }

    attemptJson.fold[Try[A]](Throw.apply, Return.apply)
  }

  implicit def enumerateCirce[A : Decoder]: Enumerate.Json[A] = {
    Enumerate.instance[A, Application.Json]((enum, cs) => {
      val parsed = cs match {
        case StandardCharsets.UTF_8 =>
          enum.map(_.asByteArray).through(byteStreamParser[Future])
        case _ =>
          enum.map(_.asString(cs)).through(stringStreamParser[Future])
      }
      parsed.through(decoder[Future, A])
    })
  }

}
