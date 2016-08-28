package io.finch.argonaut

import scala.util.{Failure, Success}

import argonaut.{CursorHistory, DecodeJson, Json}
import com.twitter.util.{Return, Throw, Try}
import io.finch.{Decode, Error}
import jawn.Parser
import jawn.support.argonaut.Parser._

trait Decoders {

  /**
   * Maps Argonaut's [[DecodeJson]] to Finch's [[Decode]].
   */
  implicit def decodeArgonaut[A](implicit d: DecodeJson[A]): Decode[A] = Decode.instance[A] { s =>
    val err: (String, CursorHistory) => Try[A] = { (str, hist) => Throw[A](Error(str)) }
    Parser.parseFromString[Json](s)(facade) match {
      case Success(value) => d.decodeJson(value).fold[Try[A]](err, Return(_))
      case Failure(error) => Throw[A](Error(error.getMessage))
    }
  }
}
