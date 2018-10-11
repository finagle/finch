package io.finch.circe

import cats.MonadError
import io.circe.Decoder
import io.circe.iteratee._
import io.circe.jawn._
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
      case _                      => decode[A](b.asString(cs))
    }

    attemptJson.fold[Either[Throwable, A]](Left.apply, Right.apply)
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
      parsed.through(decoder[F, A])
    })

}
