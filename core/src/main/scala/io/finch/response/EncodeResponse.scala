/*
 * Copyright 2014, by Vladimir Kostyukov and Contributors.
 *
 * This file is a part of a Finch library that may be found at
 *
 *      https://github.com/finagle/finch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributor(s):
 * Ryan Plessner
 * Pedro Viegas
 */

package io.finch.response

import com.twitter.finagle.Service
import com.twitter.io.Buf
import com.twitter.io.Buf.Utf8
import com.twitter.util.Future
import io.finch._
import shapeless.Witness

/**
 * An abstraction that is responsible for encoding the response of type `A`.
 */
trait EncodeResponse[-A] {
  type C <: String

  def apply(rep: A): Buf
  def contentType: String
  def charset: Option[String] = Some("utf-8")
}

object EncodeResponse {
  type Aux[A, C0] = EncodeResponse[A] { type C = C0 }

  /**
   * This is a convenience class that lets us work around the fact that Scala
   * doesn't support partial application of type parameters.
   */
  class Builder[C0 <: String](implicit w: Witness.Aux[C0]) {
    /**
     * Convenience method for creating new [[io.finch.response.EncodeResponse EncodeResponse]] instances
     * that treat `Buf` contents.
     */
    def fromBuf[A](cs: Option[String] = Some("utf-8"))(fn: A => Buf): Aux[A, C0] =
      new EncodeResponse[A] {
        type C = C0

        override def apply(rep: A): Buf = fn(rep)
        override def contentType: String = w.value
        override def charset: Option[String] = cs
      }

    /**
     * Convenience method for creating new [[io.finch.response.EncodeResponse EncodeResponse]] instances
     * that treat String contents.
     */
    def fromString[A](fn: A => String): Aux[A, C0] =
      fromBuf(Some("utf-8"))(fn andThen Utf8.apply)
  }

  /**
   * Convenience method for creating new [[io.finch.response.EncodeResponse EncodeResponse]] instances.
   */
  def apply[C <: String](implicit w: Witness.Aux[C]): Builder[C] = new Builder[C]

  /**
   * Converts [[io.finch.response.EncodeAnyResponse EncodeAnyResponse]] into
   * [[io.finch.response.EncodeResponse EncodeResponse]].
   */
  implicit def anyToConcreteEncode[A, C0 <: String](implicit e: EncodeAnyResponse.Aux[C0]): Aux[A, C0] =
    new EncodeResponse[A] {
      type C = C0

      def apply(rep: A): Buf = e(rep)
      def contentType: String = e.contentType
    }

  /**
   * Allows to pass raw strings to a [[ResponseBuilder]].
   */
  implicit val encodeString: EncodeTextResponse[String] =
    EncodeResponse("text/plain").fromString[String](identity)

  /**
   * Allows to pass `Buf` to a [[ResponseBuilder]].
   */
  implicit val encodeBuf: EncodeBinaryResponse[Buf] =
    EncodeResponse("application/octet-stream").fromBuf(None)(identity)
}

/**
 * An abstraction that is responsible for encoding the response of a generic type.
 */
trait EncodeAnyResponse {
  type C <: String

  def apply[A](rep: A): Buf
  def contentType: String
}

object EncodeAnyResponse {
  abstract class Aux[C0 <: String](implicit w: Witness.Aux[C0]) extends EncodeAnyResponse {
    type C = C0

    val contentType: String = w.value
  }
}

class TurnIntoHttp[A](val e: EncodeResponse[A]) extends Service[A, HttpResponse] {
  def apply(req: A): Future[HttpResponse] = Ok(req)(e).toFuture
}

/**
 * A service that converts an encoded object into HTTP response with status ''OK'' using an implicit
 * [[io.finch.response.EncodeResponse EncodeResponse]].
 */
object TurnIntoHttp {
  def apply[A](implicit e: EncodeResponse[A]): Service[A, HttpResponse] = new TurnIntoHttp[A](e)
}
