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
 */

package io.finch

import io.finch.request.DecodeRequest
import io.finch.response.EncodeResponse
import com.twitter.util.{Try, Throw, Return}

package object json {
  @deprecated("encodeFinchJson is deprecated as part of the Finch JSON deprecation.", "0.7.0")
  implicit val encodeFinchJson = EncodeResponse[Json]("application/json")(Json.encode)

  @deprecated("decodeFinchJson is deprecated as part of the Finch JSON deprecation.", "0.7.0")
  implicit val decodeFinchJson = DecodeRequest[Json] { json =>
    // TODO - error detail is swallowed
    Json
      .decode(json)
      .fold[Try[Json]](Throw[Json](new IllegalArgumentException("unknown error parsing JSON")))(Return(_))
  }
}
