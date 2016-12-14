package io.finch.argonaut

import argonaut.{EncodeJson, Json, PrettyParams}
import io.finch.Encode
import io.finch.internal.BufText

trait Encoders {

  protected def printer: PrettyParams

  /**
   * Maps Argonaut's [[EncodeJson]] to Finch's [[Encode]].
   */
  implicit def encodeArgonaut[A](implicit e: EncodeJson[A]): Encode.Json[A] =
    Encode.json((a, cs) => BufText(printer.pretty(e.encode(a)), cs))

  implicit def encodeExceptionArgonaut[A <: Exception]: EncodeJson[A] = new EncodeJson[A] {
    override def encode(a: A): Json =
      Json.obj(("message", Option(a.getMessage).fold(Json.jNull)(Json.jString)))
  }
}
