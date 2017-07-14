package io.finch.circe

import com.twitter.io.Buf
import io.catbird.util._
import io.circe.{Encoder, Json}
import io.finch.{Application, Encode}
import io.finch.iteratee.AsyncEncode
import java.nio.charset.Charset



trait Encoders {

  protected def print(json: Json, cs: Charset): Buf

  /**
   * Maps Circe's [[Encoder]] to Finch's [[Encode]].
   */
  implicit def encodeCirce[A](implicit e: Encoder[A]): Encode.Json[A] =
    Encode.json((a, cs) => print(e(a), cs))

  implicit def asyncEncodeCirce[A](implicit e: Encoder[A]): AsyncEncode.Json[A] =
    AsyncEncode.instance[A, Application.Json]((enum, cs) => enum.map(a => print(e(a), cs)))
}
