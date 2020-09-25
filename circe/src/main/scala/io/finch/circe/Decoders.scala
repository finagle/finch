package io.finch.circe

import java.nio.charset.StandardCharsets

import cats.MonadError
import cats.effect.Sync
import fs2.{Chunk, Stream}
import io.circe.Decoder
import io.circe.fs2
import io.circe.iteratee
import io.circe.jawn._
import io.finch.internal.HttpContent
import io.finch.{Application, Decode, DecodeStream}
import io.iteratee.Enumerator

trait Decoders {

  /**
    * Maps a Circe's [[Decoder]] to Finch's [[Decode]].
    */
  implicit def decodeCirce[A: Decoder]: Decode.Json[A] = Decode.json { (b, cs) =>
    val decoded = cs match {
      case StandardCharsets.UTF_8 => decodeByteBuffer[A](b.asByteBuffer)
      case _                      => decode[A](b.asString(cs))
    }

    decoded match {
      case Left(error) => Left(new CirceError(error))
      case right       => right
    }
  }

  implicit def enumerateCirce[F[_], A: Decoder](implicit
      F: MonadError[F, Throwable]
  ): DecodeStream.Json[Enumerator, F, A] =
    DecodeStream.instance[Enumerator, F, A, Application.Json] { (enum, cs) =>
      val parsed = cs match {
        case StandardCharsets.UTF_8 =>
          enum.map(_.asByteArray).through(iteratee.byteStreamParser[F])
        case _ =>
          enum.map(_.asString(cs)).through(iteratee.stringStreamParser[F])
      }
      parsed.through(iteratee.decoder[F, A])
    }

  implicit def fs2Circe[F[_]: Sync, A: Decoder]: DecodeStream.Json[Stream, F, A] =
    DecodeStream.instance[Stream, F, A, Application.Json] { (stream, cs) =>
      val parsed = cs match {
        case StandardCharsets.UTF_8 =>
          stream.mapChunks(chunk => chunk.flatMap(buf => Chunk.array(buf.asByteArray))).through(fs2.byteStreamParser[F])
        case _ =>
          stream.map(_.asString(cs)).through(fs2.stringStreamParser[F])
      }
      parsed.through(fs2.decoder[F, A])
    }
}
