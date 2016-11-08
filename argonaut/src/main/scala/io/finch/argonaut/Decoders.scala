package io.finch.argonaut

import argonaut.{CursorHistory, DecodeJson, Json}
import com.twitter.util.{Return, Throw, Try}
import io.finch._
import io.finch.internal.BufText
import jawn.Parser
import jawn.support.argonaut.Parser._
import scala.util.{Failure, Success}

trait Decoders {

  /**
   * Maps Argonaut's [[DecodeJson]] to Finch's [[Decode]].
   */
  implicit def decodeArgonaut[A](implicit d: DecodeJson[A]): Decode.Json[A] =
    Decode.json { (b, cs) =>
      val err: (String, CursorHistory) => Try[A] = { (str, hist) => Throw[A](Error(str)) }

      // TODO: Eliminate toString conversion
      // See https://github.com/finagle/finch/issues/511
      // Jawn can parse from ByteBuffer's if they represent UTF-8 strings.
      // We can check the charset here and do parsing w/o extra to-string conversion.
      Parser.parseFromString[Json](BufText.extract(b, cs))(facade) match {
        case Success(value) => d.decodeJson(value).fold[Try[A]](err, Return(_))
        case Failure(error) => Throw[A](Error(error.getMessage))
      }
  }
}
