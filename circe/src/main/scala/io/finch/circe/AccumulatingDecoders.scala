package io.finch.circe

import java.nio.charset.StandardCharsets

import cats.MonadError
import cats.data.Validated
import io.circe._
import io.circe.iteratee._
import io.circe.jawn.{decodeAccumulating, decodeByteBufferAccumulating}
import io.finch.{Application, Decode}
import io.finch.internal.HttpContent
import io.finch.iteratee.Enumerate
import io.iteratee.{Enumeratee, Enumerator}

trait AccumulatingDecoders {

  /**
    * Maps a Circe's [[Decoder]] to Finch's [[Decode]].
    */
  implicit def decodeCirce[A: Decoder]: Decode.Json[A] = Decode.json { (b, cs) =>
    val attemptJson = cs match {
      case StandardCharsets.UTF_8 =>
        decodeByteBufferAccumulating[A](b.asByteBuffer)
      case _ => decodeAccumulating[A](b.asString(cs))
    }

    attemptJson.fold[Either[Throwable, A]](nel => Left(Errors(nel)), Right.apply)
  }

  implicit def enumerateCirce[F[_], A: Decoder](
      implicit
      monadError: MonadError[F, Throwable]
    ): Enumerate.Json[F, A] =
    Enumerate.instance[F, A, Application.Json]((enum, cs) => {
      val parsed = cs match {
        case StandardCharsets.UTF_8 =>
          enum.map(_.asByteArray).through(byteStreamParser[F])
        case _ =>
          enum.map(_.asString(cs)).through(stringStreamParser[F])
      }
      parsed.through(decoderAccumulating[F, A])
    })

  private def decoderAccumulating[F[_], A](
      implicit
      F: MonadError[F, Throwable],
      decode: Decoder[A]
    ): Enumeratee[F, Json, A] =
    Enumeratee.flatMap(
      json =>
        decode.accumulating(json.hcursor) match {
          case Validated.Invalid(errors) => Enumerator.liftM(F.raiseError(Errors(errors)))
          case Validated.Valid(a)        => Enumerator.enumOne(a)
        }
    )

}
