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
 * Jens Halm
 */

package io.finch

import _root_.argonaut.{EncodeJson, Json, Parse, DecodeJson}
import io.finch.request.DecodeRequest
import io.finch.request.RequestError
import io.finch.response.EncodeResponse
import com.twitter.util.{Try, Throw, Return}

package object argonaut {

  /**
   * @param decode The argonaut ''DecodeJson'' to use for decoding
   * @tparam A The type of data that the ''DecodeJson'' will decode into
   * @return Create a Finch ''DecodeRequest'' from an argonaut ''DecodeJson''
   */
  implicit def decodeArgonaut[A](implicit decode: DecodeJson[A]): DecodeRequest[A] =
    DecodeRequest(
      Parse.decodeEither(_).fold[Try[A]](
        error => Throw[A](new RequestError(error)),
        Return(_)
      )
    )

  /**
   * @param encode The argonaut ''EncodeJson'' to use for decoding
   * @tparam A The type of data that the ''EncodeJson'' will encode use to create the json string
   * @return Create a Finch ''EncodeJson'' from an argonaut ''EncodeJson''
   */
  implicit def encodeArgonaut[A](implicit encode: EncodeJson[A]): EncodeResponse[A] =
    EncodeResponse.fromString[A]("application/json")(encode.encode(_).nospaces)
}
