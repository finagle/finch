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
 * Contributor(s): -
 */

package io.finch

import com.fasterxml.jackson.databind.ObjectMapper
import io.finch.request.DecodeAnyRequest
import io.finch.response.EncodeAnyResponse
import com.twitter.util.Try

import scala.reflect.ClassTag

package object jackson {

  implicit def decodeJackson(implicit mapper: ObjectMapper) = new DecodeAnyRequest {
    def apply[A: ClassTag](s: String): Try[A] = Try {
      val clazz = implicitly[ClassTag[A]].runtimeClass.asInstanceOf[Class[A]]
      mapper.readValue[A](s, clazz)
    }
  }

  implicit def encodeJackson(implicit mapper: ObjectMapper) = new EncodeAnyResponse {
    def apply[A](rep: A): String = mapper.writeValueAsString(rep)
    def contentType = "application/json"
  }
}
