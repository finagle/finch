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

import _root_.jawn.ast.{CanonicalRenderer, JValue}
import _root_.jawn.{Facade, Parser}
import io.finch.request.DecodeRequest
import io.finch.response.EncodeResponse
import com.twitter.util.{Try, Return, Throw}
import scala.util.{Success, Failure}

/**
 * This package provides support for use of the Jawn json parsing library in Finch.io.
 */
package object jawn {

  /**
   * @param facade The ''Facade'' that represents how jawn should parse json
   * @tparam J The type of data returned by the ''Facade''
   * @return Converts a jawn ''Facade'' into a ''DecodeJson''
   *
   */
  implicit def toJawnDecode[J](implicit facade: Facade[J]): DecodeRequest[J] = new DecodeRequest[J] {
    def apply(json: String): Try[J] = Parser.parseFromString(json) match {
      case Success(value) => Return(value)
      case Failure(error) => Throw(error)
    }
  }

  /**
   * The ''EncodeJawn'' object takes a ''JValue'' (part of Jawn's ast package) and
   * returns the string representation of that value.
   */
  implicit val EncodeJawn: EncodeResponse[JValue] = new EncodeResponse[JValue] {
    def apply(json: JValue): String = CanonicalRenderer.render(json)

    override def contentType: String = "application/json"
  }
}
