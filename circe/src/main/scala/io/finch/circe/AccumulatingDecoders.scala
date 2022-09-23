package io.finch.circe

import cats.MonadThrow
import cats.data.Validated
import io.circe._
import io.circe.iteratee._
import io.circe.jawn.{decodeAccumulating, decodeByteBufferAccumulating}
import io.finch.internal.HttpContent
import io.finch.{Application, Decode, DecodeStream}
import io.iteratee.{Enumeratee, Enumerator}

import java.nio.charset.StandardCharsets

trait AccumulatingDecoders {

  /** Maps a Circe's [[io.circe.Decoder]] to Finch's [[Decode]]. */
  implicit def decodeCirce[A: Decoder]: Decode.Json[A] =
    Decode.json { (b, cs) =>
      (cs match {
        case StandardCharsets.UTF_8 => decodeByteBufferAccumulating[A](b.asByteBuffer)
        case _                      => decodeAccumulating[A](b.asString(cs))
      }).fold(errors => Left(Errors(errors)), Right.apply)
    }

  implicit def iterateeCirceDecoder[F[_]: MonadThrow, A: Decoder]: DecodeStream.Json[Enumerator, F, A] =
    DecodeStream.instance[Enumerator, F, A, Application.Json] { (enum, cs) =>
      (cs match {
        case StandardCharsets.UTF_8 => enum.map(_.asByteArray).through(byteStreamParser[F])
        case _                      => enum.map(_.asString(cs)).through(stringStreamParser[F])
      }).through(decoderAccumulating[F, A])
    }

  private def decoderAccumulating[F[_]: MonadThrow, A: Decoder]: Enumeratee[F, Json, A] =
    Enumeratee.flatMap { json =>
      Decoder[A].decodeAccumulating(json.hcursor) match {
        case Validated.Invalid(errors) => Enumerator.liftM(MonadThrow[F].raiseError(Errors(errors)))
        case Validated.Valid(a)        => Enumerator.enumOne(a)
      }
    }
}
