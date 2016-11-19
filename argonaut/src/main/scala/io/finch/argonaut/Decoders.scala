package io.finch.argonaut

import argonaut.{CursorHistory, DecodeJson, Json}
import com.twitter.finagle.netty3.ChannelBufferBuf
import com.twitter.util.{Return, Throw, Try}
import io.finch._
import io.finch.internal.BufText
import java.nio.charset.StandardCharsets
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
      val attemptJson = cs match {
        case StandardCharsets.UTF_8 =>
          Parser.parseFromByteBuffer[Json](ChannelBufferBuf.Owned.extract(b).toByteBuffer())(facade)
        case _ => Parser.parseFromString[Json](BufText.extract(b, cs))(facade)
      }
      attemptJson match {
        case Success(value) => d.decodeJson(value).fold[Try[A]](err, Return(_))
        case Failure(error) => Throw[A](Error(error.getMessage))
      }
  }
}
