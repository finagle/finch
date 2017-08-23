package io.finch.argonaut

import argonaut._
import argonaut.DecodeJson
import cats.syntax.either._
import com.twitter.util.{Return, Throw}
import io.finch._
import io.finch.internal.HttpContent

trait Decoders {

  /**
   * Maps Argonaut's [[DecodeJson]] to Finch's [[Decode]].
   */
  implicit def decodeArgonaut[A](implicit d: DecodeJson[A]): Decode.Json[A] =
    Decode.json { (b, cs) =>
      Parse.parse(b.asString(cs)).flatMap(_.as[A].result.leftMap(_._1)) match {
        case Right(result) => Return(result)
        case Left(error) => Throw(new Exception(error))
      }
  }
}
