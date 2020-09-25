package io.finch.argonaut

import argonaut.DecodeJson
import argonaut._
import cats.syntax.either._
import io.finch._
import io.finch.internal.HttpContent

trait Decoders {

  /**
    * Maps Argonaut's [[DecodeJson]] to Finch's [[Decode]].
    */
  implicit def decodeArgonaut[A](implicit d: DecodeJson[A]): Decode.Json[A] =
    Decode.json { (b, cs) =>
      Parse.parse(b.asString(cs)).flatMap(_.as[A].result.leftMap(_._1)) match {
        case Right(result) => Right(result)
        case Left(error)   => Left(new Exception(error))
      }
    }
}
