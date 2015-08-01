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

package io.finch

import com.twitter.util.Try
import io.finch.request.DecodeRequest
import io.finch.response.EncodeResponse
import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.jackson.Serialization._

package object json4s {

  /**
   * @param formats json4s `Formats` to use for decoding
   * @tparam A the type of data to decode into
   */
  //@TODO get rid of Manifest as soon as json4s migrates to new reflection API
  implicit def decodeJson[A : Manifest](implicit formats: Formats): DecodeRequest[A] = DecodeRequest(
    input => Try(JsonMethods.parse(input).extract[A])
  )

  /**
   * @param formats json4s `Formats` to use for decoding
   * @tparam A the type of data to encode
   * @return
   */
  implicit def encodeJson[A <: AnyRef](implicit formats: Formats): EncodeResponse[A] =
    EncodeResponse.fromString[A]("application/json") { write(_) }
}
