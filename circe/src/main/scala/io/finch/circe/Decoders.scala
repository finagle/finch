package io.finch.circe

import cats.MonadError
import cats.effect.Sync
import fs2.Stream
import io.circe.Decoder
import io.circe.fs2
import io.circe.iteratee
import io.circe.jawn._
import io.finch.{Application, Decode}
import io.finch.internal.HttpContent
import io.finch.streaming.StreamDecode
import io.iteratee.Enumerator
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

    attemptJson.fold[Either[Throwable, A]](Left.apply, Right.apply)
  }

  implicit def enumerateCirce[F[_], A : Decoder](implicit
    monadError: MonadError[F, Throwable]
  ): StreamDecode.Json[Enumerator, F, A] = {
    StreamDecode.instance[Enumerator, F, A, Application.Json]((enum, cs) => {
      val parsed = cs match {
        case StandardCharsets.UTF_8 =>
          enum.map(_.asByteArray).through(iteratee.byteStreamParser[F])
        case _ =>
          enum.map(_.asString(cs)).through(iteratee.stringStreamParser[F])
      }
      parsed.through(iteratee.decoder[F, A])
    })
  }

  implicit def fs2Circe[F[_] : Sync, A : Decoder](implicit
    monadError: MonadError[F, Throwable]
  ): StreamDecode.Json[Stream, F, A] = {
    StreamDecode.instance[Stream, F, A, Application.Json]((stream, cs) => {
      val parsed = cs match {
        case StandardCharsets.UTF_8 =>
          stream
            .flatMap(buf => Stream.fromIterator(buf.asByteArray.toIterator))
            .through(fs2.byteStreamParser[F])
        case _ =>
          stream
            .map(_.asString(cs))
            .through(fs2.stringStreamParser[F])
      }
      parsed.through(fs2.decoder[F, A])
    })
  }

}
