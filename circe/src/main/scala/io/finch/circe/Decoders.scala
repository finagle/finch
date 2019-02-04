package io.finch.circe

import cats.MonadError
import cats.effect.Sync
import fs2.{Chunk, Stream}
import io.circe.Decoder
import io.circe.fs2
import io.circe.iteratee
import io.circe.jawn._
import io.finch.{Application, Decode, DecodeStream}
import io.finch.internal.HttpContent
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

  implicit def enumerateCirce[F[_], A: Decoder](implicit
    F: MonadError[F, Throwable],
    FF: DecodeStream.FromReader[F, Enumerator]
  ): DecodeStream.Json[F, Enumerator, A] =
    DecodeStream.instance[F, Enumerator, A, Application.Json]((r, cs) => {
      val enum = FF(r)
      val parsed = cs match {
        case StandardCharsets.UTF_8 =>
          enum.map(_.asByteArray).through(iteratee.byteStreamParser[F])
        case _ =>
          enum.map(_.asString(cs)).through(iteratee.stringStreamParser[F])
      }
      parsed.through(iteratee.decoder[F, A])
    })

  implicit def fs2Circe[F[_]: Sync, A: Decoder](implicit
    FF: DecodeStream.FromReader[F, Stream]
  ): DecodeStream.Json[F, Stream, A] =
    DecodeStream.instance[F, Stream, A, Application.Json]((r, cs) => {
      val stream = FF(r)
      val parsed = cs match {
        case StandardCharsets.UTF_8 =>
          stream
            .mapChunks(chunk => chunk.flatMap(buf => Chunk.array(buf.asByteArray)))
            .through(fs2.byteStreamParser[F])
        case _ =>
          stream
            .map(_.asString(cs))
            .through(fs2.stringStreamParser[F])
      }
      parsed.through(fs2.decoder[F, A])
    })
}
