/*
 * Copyright 2015 Vladimir Kostyukov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.finch.request

import com.twitter.util.Try
import scala.reflect.ClassTag

/**
 * An abstraction that is responsible for decoding the request of type `A`.
 */
trait DecodeRequest[A] {
  def apply(req: String): Try[A]
}

object DecodeRequest {
  /**
   * Convenience method for creating new [[io.finch.request.DecodeRequest DecodeRequest]] instances.
   */
  def apply[A](f: String => Try[A]): DecodeRequest[A] = new DecodeRequest[A] {
    def apply(value: String): Try[A] = f(value)
  }

  /**
   * A [[io.finch.request.DecodeRequest DecodeRequest]] instance for `String`.
   */
  implicit val decodeString: DecodeRequest[String] = DecodeRequest { s => Try(s) }

  /**
   * A [[io.finch.request.DecodeRequest DecodeRequest]] instance for `Int`.
   */
  implicit val decodeInt: DecodeRequest[Int] = DecodeRequest { s => Try(s.toInt) }

  /**
   * A [[io.finch.request.DecodeRequest DecodeRequest]] instance for `Long`.
   */
  implicit val decodeLong: DecodeRequest[Long] = DecodeRequest { s => Try(s.toLong) }

  /**
   * A [[io.finch.request.DecodeRequest DecodeRequest]] instance for `Float`.
   */
  implicit val decodeFloat: DecodeRequest[Float] = DecodeRequest { s => Try(s.toFloat) }

  /**
   * A [[DecodeRequest]] instance for `Double`.
   */
  implicit val decodeDouble: DecodeRequest[Double] = DecodeRequest { s => Try(s.toDouble) }

  /**
   * A [[io.finch.request.DecodeRequest DecodeRequest]] instance for `Boolean`.
   */
  implicit val decodeBoolean: DecodeRequest[Boolean] = DecodeRequest { s => Try(s.toBoolean) }

  /**
   * Creates a [[DecodeRequest]] from [[DecodeAnyRequest ]].
   */
  implicit def decodeRequestFromAnyDecode[A](implicit
    d: DecodeAnyRequest,
    tag: ClassTag[A]
  ): DecodeRequest[A] = new DecodeRequest[A] {
    def apply(req: String): Try[A] = d(req)(tag)
  }
}

/**
 * An abstraction that is responsible for decoding the request of general type.
 */
trait DecodeAnyRequest {
  def apply[A: ClassTag](req: String): Try[A]
}
