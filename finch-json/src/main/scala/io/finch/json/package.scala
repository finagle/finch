package io.finch

import scala.util.parsing.json.{JSONType, JSONArray, JSONObject, JSONFormat}

package object json {
  /**
   * A default json formatter that doesn't escape forward slashes.
   */
  object JsonFormatter extends JSONFormat.ValueFormatter {

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
    def apply(json: JSONType): String = json.toString(JsonFormatter)
  }

  implicit object DecodeDeprecatedJson extends DecodeJson[JSONType] {
    // TODO: figure out how to do the parsing
    def apply(json: String): JSONType = JSONObject(Map.empty)
  }
}
