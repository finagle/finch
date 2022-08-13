package io.finch.circe

import cats.MonadThrow
import cats.effect.Sync
import cats.syntax.all._
import fs2.{Chunk, Stream}
import io.circe.jawn._
import io.circe.{Decoder, fs2, iteratee}
import io.finch.internal.HttpContent
import io.finch.{Application, Decode, DecodeStream}
import io.iteratee.Enumerator

import java.nio.charset.StandardCharsets

trait Decoders {

  /** Maps a Circe's [[Decoder]] to Finch's [[Decode]]. */
  implicit def decodeCirce[A: Decoder]: Decode.Json[A] =
    Decode.json { (b, cs) =>
      (cs match {
        case StandardCharsets.UTF_8 => decodeByteBuffer[A](b.asByteBuffer)
        case _                      => decode[A](b.asString(cs))
      }).leftMap(new CirceError(_))
    }

  implicit def enumerateCirce[F[_]: MonadThrow, A: Decoder]: DecodeStream.Json[Enumerator, F, A] =
    DecodeStream.instance[Enumerator, F, A, Application.Json] { (enum, cs) =>
      (cs match {
        case StandardCharsets.UTF_8 => enum.map(_.asByteArray).through(iteratee.byteStreamParser[F])
        case _                      => enum.map(_.asString(cs)).through(iteratee.stringStreamParser[F])
      }).through(iteratee.decoder[F, A])
    }

  implicit def fs2Circe[F[_]: Sync, A: Decoder]: DecodeStream.Json[Stream, F, A] =
    DecodeStream.instance[Stream, F, A, Application.Json] { (stream, cs) =>
      (cs match {
        case StandardCharsets.UTF_8 =>
          stream.mapChunks(chunk => chunk.flatMap(buf => Chunk.array(buf.asByteArray))).through(fs2.byteStreamParser[F])
        case _ =>
          stream.map(_.asString(cs)).through(fs2.stringStreamParser[F])
      }).through(fs2.decoder[F, A])
    }
}
