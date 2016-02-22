package io.finch.argonaut

import argonaut.{EncodeJson, PrettyParams}
import com.twitter.io.Buf
import io.finch.Encode

trait Encoders {

  protected def printer: PrettyParams

  /**
   * Maps Argonaut's [[EncodeJson]] to Finch's [[Encode]].
   */
  implicit def encodeArgonaut[A](implicit e: EncodeJson[A]): Encode.ApplicationJson[A] =
    Encode.applicationJson(a => Buf.Utf8(printer.pretty(e.encode(a))))
}
