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

import io.finch.response._
import scala.util.parsing.json.JSONFormat
import scala.util.parsing.json.JSONArray
import scala.util.parsing.json.JSONObject
import com.twitter.finagle.Service

package object json {

  /**
   * Intrusive json null value.
   */
  object JsonNull {
    override def toString = null
  }

  /**
   * A companion object for json object.
   */
  object JsonObject {

    /**
     * Creates a json object of given sequence of properties ''args''. Every
     * argument/property is a pair of ''tag'' and ''value'' associated with it.
     * It's possible to pass a complete json path (separated by dot) as ''tag''.
     *
     * @param args a sequence of json properties
     *
     * @return a json object
     */
    def apply(args: (String, Any)*) = {
      def loop(path: List[String], value: Any): Map[String, Any] = path match {
        case tag :: Nil => Map(tag -> value)
        case tag :: tail => Map(tag -> JSONObject(loop(tail, value)))
      }

      val jsonSeq = args.flatMap {
        case (path, value) =>
          Seq(JSONObject(loop(path.split('.').toList, if (value == null) JsonNull else value)))
      }

      jsonSeq.foldLeft(JsonObject.empty) { mergeRight }
    }

    /**
     * Creates an empty json object
     *
     * @return an empty json object
     */
    def empty = JSONObject(Map.empty[String, Any])

    def unapply(outer: Any): Option[JSONObject] = outer match {
      case inner: JSONObject => Some(inner)
      case _ => None
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
    def mergeRight(a: JSONObject, b: JSONObject) = mergeLeft(b, a)

    /**
     * Deeply merges given json objects ''a'' and ''b'' into a single json object.
     * In case of conflict tag the value of a left json object will be taken.
     *
     * @param a the left json object
     * @param b the right json object
     *
     * @return a merged json object
     */
    def mergeLeft(a: JSONObject, b: JSONObject): JSONObject = {
      def loop(aa: Map[String, Any], bb: Map[String, Any]): Map[String, Any] =
        if (aa.isEmpty) bb
        else if (bb.isEmpty) aa
        else {
          val (tag, value) = aa.head
          if (!bb.contains(tag)) loop(aa.tail, bb + (tag -> value))
          else (value, bb(tag)) match {
            case (ja: JSONObject, jb: JSONObject) =>
              loop(aa.tail, bb + (tag -> JSONObject(loop(ja.obj, jb.obj))))
            case (_, _) => loop(aa.tail, bb + (tag -> value))
          }
        }

      JSONObject(loop(a.obj, b.obj))
    }
  }

  /**
   * A companion object for json array.
   */
  object JsonArray {

    /**
     * Creates a new json array of given sequence of items ''args''.
     *
     * @param args sequence of items in the array
     *
     * @return a new json object
     */
    def apply(args: Any*) = JSONArray(args.toList)

    /**
     * Creates an empty json array.
     *
     * @return an empty json array.
     */
    def empty = JSONArray(List.empty[Any])

    def unapply(outer: Any): Option[JSONArray] = outer match {
      case inner: JSONArray => Some(inner)
      case _ => None
    }

    /**
     * Concatenates two given arrays ''a'' and ''b''.
     *
     * @param a the left array
     * @param b the right array
     *
     * @return a concatenated array
     */
    def concat(a: JSONArray, b: JSONArray) = JSONArray(a.list ::: b.list)
  }

  /**
   * A json formatter that primary escape special chars.
   */
  trait JsonFormatter extends JSONFormat.ValueFormatter { self =>
    def apply(x: Any) = x match {
      case s: String => "\"" + formatString(s) + "\""
      case o: JSONObject => o.toString(self)
      case a: JSONArray => a.toString(self)
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
    def escapeChar: PartialFunction[Char, String]
  }

  /**
   * A default json formatter that doesn't escape forward slashes.
   */
  object DefaultJsonFormatter extends JsonFormatter {
    def escapeChar = {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
    }
  }

  /**
   * A service that converts JSON into HTTP response with status ''OK''.
   *
   * @param formatter a json formatter
   */
  case class TurnJsonIntoHttpWithFormatter(formatter: JsonFormatter = DefaultJsonFormatter)
      extends Service[JsonResponse, HttpResponse] {

    def apply(req: JsonResponse) = Ok(req, formatter).toFuture
  }

  /**
   * A default instance of ''TurnJsonIntoHttpWithFormatter''.
   */
  object TurnJsonIntoHttp extends TurnJsonIntoHttpWithFormatter
}
