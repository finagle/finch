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
import com.twitter.finagle.httpx.Response
import com.twitter.io.Buf
import com.twitter.io.Buf.Utf8
import com.twitter.util.Future
import io.finch._

/**
 * An abstraction that is responsible for encoding the response of type `A`.
 */
trait EncodeResponse[-A] {
  def apply(rep: A): Buf
  def contentType: String
  def charset: Option[String] = Some("utf-8")
}

object EncodeResponse {
  /**
   * Convenience method for creating new [[io.finch.response.EncodeResponse EncodeResponse]] instances.
   */
  def apply[A](ct: String, cs: Option[String] = Some("utf-8"))(fn: A => Buf): EncodeResponse[A] =
    new EncodeResponse[A] {
      override def apply(rep: A): Buf = fn(rep)
      override def contentType: String = ct
      override def charset: Option[String] = cs
    }

  /**
   * Convenience method for creating new [[io.finch.response.EncodeResponse EncodeResponse]] instances
   * that treat String contents.
   */
  def fromString[A](ct: String, cs: Option[String] = Some("utf-8"))(fn: A => String): EncodeResponse[A] =
    apply(ct, cs)(fn andThen Utf8.apply)

  /**
   * Converts [[io.finch.response.EncodeAnyResponse EncodeAnyResponse]] into
   * [[io.finch.response.EncodeResponse EncodeResponse]].
   */
  implicit def anyToConcreteEncode[A](implicit e: EncodeAnyResponse): EncodeResponse[A] =
    new EncodeResponse[A] {
      def apply(rep: A): Buf = e(rep)
      def contentType: String = e.contentType
    }

  /**
   * Allows to pass raw strings to a [[ResponseBuilder]].
   */
  implicit val encodeString: EncodeResponse[String] = EncodeResponse.fromString[String]("text/plain")(identity)

  /**
   * Allows to pass `Buf` to a [[ResponseBuilder]].
   */
  implicit val encodeBuf: EncodeResponse[Buf] =
    EncodeResponse("application/octet-stream", None)(identity)
}

/**
 * An abstraction that is responsible for encoding the response of a generic type.
 */
trait EncodeAnyResponse {
  def apply[A](rep: A): Buf
  def contentType: String
}

class TurnIntoHttp[A](val e: EncodeResponse[A]) extends Service[A, Response] {
  def apply(req: A): Future[Response] = Ok(req)(e).toFuture
}

/**
 * A service that converts an encoded object into HTTP response with status ''OK'' using an implicit
 * [[io.finch.response.EncodeResponse EncodeResponse]].
 */
object TurnIntoHttp {
  def apply[A](implicit e: EncodeResponse[A]): Service[A, Response] = new TurnIntoHttp[A](e)
}
