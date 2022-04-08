package io.finch.argonaut

import argonaut.{EncodeJson, PrettyParams}
import com.twitter.io.Buf
import io.finch.Encode

trait Encoders {

  protected def printer: PrettyParams

  /** Maps Argonaut's [[EncodeJson]] to Finch's [[Encode]].
    */
  implicit def encodeArgonaut[A](implicit e: EncodeJson[A]): Encode.Json[A] =
    Encode.json((a, cs) => Buf.ByteArray.Owned(printer.pretty(e.encode(a)).getBytes(cs.name)))
}
