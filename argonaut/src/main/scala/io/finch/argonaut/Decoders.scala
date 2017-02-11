package io.finch.argonaut

import argonaut.{CursorHistory, DecodeJson, Json}
import argonaut.JawnParser._
import com.twitter.util.{Return, Throw, Try}
import io.finch._
import io.finch.internal.HttpContent
import java.nio.charset.StandardCharsets
import jawn.Parser
import scala.util.{Failure, Success}

trait Decoders {

  /**
   * Maps Argonaut's [[DecodeJson]] to Finch's [[Decode]].
   */
  implicit def decodeArgonaut[A](implicit d: DecodeJson[A]): Decode.Json[A] =
    Decode.json { (b, cs) =>
      val err: (String, CursorHistory) => Try[A] = { (str, hist) => Throw(new Exception(str)) }

      val attemptJson = cs match {
        case StandardCharsets.UTF_8 =>
          Parser.parseFromByteBuffer[Json](b.asByteBuffer)(facade)
        case _ =>
          Parser.parseFromString[Json](b.asString(cs))(facade)
      }

      attemptJson match {
        case Success(value) => d.decodeJson(value).fold(err, Return.apply)
        case Failure(error) => Throw(error)
      }
  }
}
