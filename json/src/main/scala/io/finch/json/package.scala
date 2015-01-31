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
import com.twitter.util.{Try,Throw,Return}
import io.finch.request.BodyNotParsed

package object json {
  implicit val encodeFinchJson = new EncodeResponse[Json] {
    def apply(json: Json): String = Json.encode(json)

    override def contentType: String = "application/json"
  }

  implicit val decodeFinchJson = new DecodeRequest[Json] {
    def apply(json: String): Try[Json] = Json.decode(json)
      .fold[Try[Json]](Throw(BodyNotParsed))(Return(_)) // TODO - error detail is swallowed
  }
}
