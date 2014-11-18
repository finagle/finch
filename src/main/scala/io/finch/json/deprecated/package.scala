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

package io.finch.json

import scala.util.parsing.json.{JSONType, JSONArray, JSONObject, JSONFormat}

package object deprecated {

  /**
   * A default json formatter that doesn't escape forward slashes.
   */
  object DeprecatedJsonFormatter extends JSONFormat.ValueFormatter {

    def apply(x: Any) = x match {
      case s: String => "\"" + formatString(s) + "\""
      case o: JSONObject => o.toString(this)
      case a: JSONArray => a.toString(this)
      case other => other.toString
    }

    def formatString(s: String) = s flatMap { escapeOrSkip(_) }

    def escapeOrSkip: PartialFunction[Char, String] = escapeChar orElse {
      case c => c.toString
    }

    /**
     * A partial function that defines a set of rules on how to escape the
     * special characters in a string.
     *
     * @return an escaped char represented as a string
     */
    def escapeChar: PartialFunction[Char, String] = {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
    }
  }

  implicit object EncodeDeprecatedJson extends EncodeJson[JSONType] {
    def apply(json: JSONType): String = json.toString(DeprecatedJsonFormatter)
  }

  implicit object DecodeDeprecatedJson extends DecodeJson[JSONType] {
    // TODO: figure out how to do the parsing
    def apply(json: String): JSONType = JSONObject(Map.empty)
  }
}
