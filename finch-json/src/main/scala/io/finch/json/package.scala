package io.finch

package object json {

  /**
   *
   */
  sealed trait Json

  case class JsonObject(map: Map[String, Any]) extends Json

  case class JsonArray(list: List[Any]) extends Json

  object JsonNull extends Json

  object Json {

    /**
     * Creates an empty json object
     *
     * @return an empty json object
     */
    def emptyObject = JsonObject(Map.empty[String, Any])

    /**
     * Creates an empty json array.
     *
     * @return an empty json array.
     */
    def emptyArray = JsonArray(List.empty[Any])

    /**
     * Creates a new json array of given sequence of items ''args''.
     *
     * @param args sequence of items in the array
     *
     * @return a new json array
     */
    def arr(args: Any*) = JsonArray(args.toList)

    /**
     * Creates a json object of given sequence of properties ''args''. Every
     * argument/property is a pair of ''tag'' and ''value'' associated with it.
     * It's possible to pass a complete json path (separated by dot) as ''tag''.
     *
     * @param args a sequence of json properties
     *
     * @return a json object
     */
    def obj(args: (String, Any)*) = {
      def loop(path: List[String], value: Any): Map[String, Any] = path match {
        case tag :: Nil => Map(tag -> value)
        case tag :: tail => Map(tag -> JsonObject(loop(tail, value)))
      }

      val jsonSeq = args.flatMap {
        case (path, value) =>
          Seq(JsonObject(loop(path.split('.').toList, if (value == null) JsonNull else value)))
      }

      jsonSeq.foldLeft(Json.emptyObject) { Json.mergeRight }
    }


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

    /**
     * Deeply merges given json objects ''a'' and ''b'' into a single json object.
     * In case of conflict tag the value of a right json object will be taken.
     *
     * @param a the left json object
     * @param b the right json object
     *
     * @return a merged json object
     */
    def mergeRight(a: JsonObject, b: JsonObject) = mergeLeft(b, a)

    /**
     * Deeply merges given json objects ''a'' and ''b'' into a single json object.
     * In case of conflict tag the value of a left json object will be taken.
     *
     * @param a the left json object
     * @param b the right json object
     *
     * @return a merged json object
     */
    def mergeLeft(a: JsonObject, b: JsonObject): JsonObject = {
      def loop(aa: Map[String, Any], bb: Map[String, Any]): Map[String, Any] =
        if (aa.isEmpty) bb
        else if (bb.isEmpty) aa
        else {
          val (tag, value) = aa.head
          if (!bb.contains(tag)) loop(aa.tail, bb + (tag -> value))
          else (value, bb(tag)) match {
            case (ja: JsonObject, jb: JsonObject) =>
              loop(aa.tail, bb + (tag -> JsonObject(loop(ja.map, jb.map))))
            case (_, _) => loop(aa.tail, bb + (tag -> value))
          }
        }

      JsonObject(loop(a.map, b.map))
    }

    /**
     * Concatenates two given arrays ''a'' and ''b''.
     *
     * @param a the left array
     * @param b the right array
     *
     * @return a concatenated array
     */
    def concat(a: JsonArray, b: JsonArray) = JsonArray(a.list ::: b.list)
  }

  implicit object EncodeDeprecatedJson extends EncodeJson[Json] {
    def apply(json: Json): String = Json.encode(json)
  }

  implicit object DecodeDeprecatedJson extends DecodeJson[Json] {
    def apply(json: String): Json = Json.decode(json)
  }
}
