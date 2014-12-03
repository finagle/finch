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

import io.finch.json.{DecodeJson, EncodeJson}
import _root_.jawn.ast.{CanonicalRenderer, JValue}
import _root_.jawn.{Facade, Parser}

/**
 * This package provides support for use of the Jawn json parsing library in Finch.io.
 */
package object jawn {

  /**
   * The ''DecodeJawn'' object takes a jawn ''Facade'' and creates a ''DecodeJson''
   * from it. the ''Facade'' can be provided implicitly.
   */
  object DecodeJawn {
    def apply[J](implicit facade: Facade[J]) = new DecodeJson[J] {

      /**
       * @param json a string that represents json
       * @return Takes a string that represents json and attempts to parse it using
       *         the Jawn parser and the given ''Facade''. It will return whatever
       *         format the Facade produces.
       */
      def apply(json: String): Option[J] = Parser.parseFromString(json).toOption
    }
  }

  /**
   * The ''EncodeJawn'' object takes a ''JValue'' (part of Jawn's ast package) and
   * returns the string representation of that value.
   */
  object EncodeJawn extends EncodeJson[JValue] {
    def apply(json: JValue): String = CanonicalRenderer.render(json)
  }
}