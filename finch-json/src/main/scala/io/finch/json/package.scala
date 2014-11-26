package io.finch

package object json {

  sealed trait Json

  object Json {

    def decode(s: String): Json = Json.emptyObject

    def encode(j: Json): String = {
      def escape(s: String) = s flatMap {
        case '"'  => "\\\""
        case '\\' => "\\\\"
        case '\b' => "\\b"
        case '\f' => "\\f"
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case c => c.toString
      }

      def wire(any: Any): String = any match {
        case s: String => escape(s)
        case JsonObject(map) => "{" + map.map({ case (k,v) => wire(k.toString) + " : " + wire(v) }) + "}"
        case JsonArray(list) => "[" + list.map(wire).mkString(",") + "]"
        case other => other.toString
      }

      wire(j)
    }

    def emptyObject = JsonObject()
    def emptyArray = JsonArray()
  }

  case class JsonObject(map: Map[String, Any] = Map.empty[String, Any]) extends Json {
    def this(args: (String, Any)*) = this(args.toMap)
  }

  case class JsonArray(list: List[Any] = List.empty[Any]) extends Json {
    def this(args: Any*) = this(args.toList)
  }

  object JsonNull extends Json

  implicit object EncodeDeprecatedJson extends EncodeJson[Json] {
    def apply(json: Json): String = Json.encode(json)
  }

  implicit object DecodeDeprecatedJson extends DecodeJson[Json] {
    def apply(json: String): Json = Json.decode(json)
  }
}
