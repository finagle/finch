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
 */

package io.finch.json

import scala.util.parsing.json.JSON

/**
 * An immutable Json API.
 */
sealed trait Json {

  /**
   * The string representation of this json object.
   */
  override def toString: String = Json.encode(this)

  /**
   * Merges this json object with given `that` one.
   * See [[Json.mergeLeft]] for details.
   */
  def merge(that: Json): Json = Json.mergeLeft(this, that)

  /**
   * Concatenates this json object with given `that` one.
   * See [[Json.concatLeft]] for details.
   */
  def concat(that: Json): Json = Json.concatLeft(this, that)

  /**
   * A compressed version of this json object.
   * See [[Json.compress]] for details.
   */
  def compressed: Json = Json.compress(this)

  /**
   * Queries this json object by the given `path`.
   *
   * @param path a path to json value
   *
   * @return an option of json value
   */
  def apply[A](path: String): Option[A] = {
    def loop(path: List[String], outer: Map[String, Any]): Option[A] = path match {
      case tag :: Nil => outer.get(tag) map { _.asInstanceOf[A] }
      case tag :: tail => outer.get(tag) match {
        case Some(JsonObject(inner)) => loop(tail, inner)
        case _ => None
      }
    }

    // for now we don't query arrays
    this match {
      case JsonObject(map) => loop(path.split('.').toList, map)
      case _ => None
    }
  }
}

/**
 *  A json object with underlying `map`.
 */
case class JsonObject(map: Map[String, Any]) extends Json

/**
 * A json array with underlying `list`.
 */
case class JsonArray(list: List[Any]) extends Json

/**
 * A json null value.
 */
object JsonNull extends Json

/**
 * A json companion object, which in an entry point into JSON API.
 */
object Json {

  /**
   * Creates an empty json object
   *
   * @return an empty json object
   */
  def emptyObject: Json = JsonObject(Map.empty[String, Any])

  /**
   * Creates an empty json array.
   *
   * @return an empty json array.
   */
  def emptyArray: Json = JsonArray(List.empty[Any])

  /**
   * Creates a new json array of given sequence of items ''args''.
   *
   * @param args sequence of items in the array
   *
   * @return a new json array
   */
  def arr(args: Any*): Json = JsonArray(args.toList)

  /**
   * Creates a json object of given sequence of properties ''args''. Every
   * argument/property is a pair of ''tag'' and ''value'' associated with it.
   * It's possible to pass a complete json path (separated by dot) as ''tag''.
   *
   * @param args a sequence of json properties
   *
   * @return a json object
   */
  def obj(args: (String, Any)*): Json = {
    def loop(path: List[String], value: Any): Map[String, Any] = path match {
      case tag :: Nil => Map(tag -> value)
      case tag :: tail => Map(tag -> JsonObject(loop(tail, value)))
    }

    val jsonSeq = args.flatMap {
      case (path, value) =>
        Seq(JsonObject(loop(path.split('.').toList, if (value == null) JsonNull else value)))
    }

    jsonSeq.foldLeft(Json.emptyObject) { Json.mergeRight(_, _) }
  }

  /**
   * Decodes a json object from the given string `s`.
   *
   * @param s a string representation of a json object
   *
   * @return a decoded json object wrapping in option
   */
  def decode(s: String): Option[Json] = {
    def wrap(a: Any): Any = a match {
      case list: List[_] => JsonArray(list map wrap)
      case map: Map[_, _] => JsonObject(map map {
        case (k, v) => k.toString -> wrap(v)
      })
      case other => other
    }

    JSON.parseFull(s) match {
      case Some(json) => Some(wrap(json).asInstanceOf[Json])
      case None => None
    }
  }

  /**
   * Encodes the given json object `j` into its string representation.
   *
   * @param j a json object to encode
   *
   * @return a string representation of a json object
   */
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
      case JsonObject(map) => "{" + map.map({ case (k, v) => wire(k.toString) + ":" + wire(v) }).mkString(",") + "}"
      case JsonArray(list) => "[" + list.map(wire).mkString(",") + "]"
      case JsonNull => "null"
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
  def mergeRight(a: Json, b: Json): Json = mergeLeft(b, a)

  /**
   * Deeply merges given json objects ''a'' and ''b'' into a single json object.
   * In case of conflict tag the value of a left json object will be taken.
   *
   * @param a the left json object
   * @param b the right json object
   *
   * @return a merged json object
   */
  def mergeLeft(a: Json, b: Json): Json = {
    def loop(aa: Map[String, Any], bb: Map[String, Any]): Map[String, Any] =
      if (aa.isEmpty) bb
      else if (bb.isEmpty) aa
      else {
        val (tag, value) = aa.head
        if (!bb.contains(tag)) loop(aa.tail, bb + (tag -> value))
        else (value, bb(tag)) match {
          case (JsonObject(aaa), JsonObject(bbb)) =>
            loop(aa.tail, bb + (tag -> JsonObject(loop(aaa, bbb))))
          case (_, _) => loop(aa.tail, bb + (tag -> value))
        }
      }

    (a, b) match {
      case (JsonObject(aa), JsonObject(bb)) => JsonObject(loop(aa, bb))
      case (aa, JsonNull) => aa
      case (JsonNull, bb) => bb
      case _ => throw new IllegalArgumentException("Can not merge json arrays with anything.")
    }
  }

  /**
   * Concatenates two given json object ''a'' and ''b'' with ''b'' object priority.
   *
   * @param a the left object
   * @param b the right object
   *
   * @return a concatenated array
   */
  def concatRight(a: Json, b: Json): Json = concatLeft(b, a)

  /**
   * Concatenates two given json object ''a'' and ''b'' with ''a'' object in priority.
   *
   * @param a the left object
   * @param b the right object
   *
   * @return a concatenated array
   */
  def concatLeft(a: Json, b: Json): Json = (a, b) match {
    case (JsonObject(aa), JsonObject(bb)) => JsonObject(aa ++ bb)
    case (JsonArray(aa), JsonArray(bb)) => JsonArray(aa ::: bb)
    case (aa, JsonNull) => aa
    case (JsonNull, bb) => bb
    case _ => throw new IllegalArgumentException("Can not concat json arrays with json objects.")
  }

  /**
   * Removes all null-value properties from this json object.
   *
   * @return a compressed json object
   */
  def compress(j: Json): Json = {
    def loop(obj: Map[String, Any]): Map[String, Any] = obj.flatMap {
      case (t, JsonNull) => Map.empty[String, Any]
      case (tag, JsonObject(map)) =>
        val o = loop(map)
        if (o.isEmpty) Map.empty[String, Any]
        else Map(tag -> JsonObject(o))
      case (tag, value) => Map(tag -> value)
    }

    j match {
      case JsonNull => JsonNull
      case JsonObject(map) => JsonObject(loop(map))
      case JsonArray(list) => JsonArray(list.map {
        case jj: Json => Json.compress(jj)
        case ii => ii
      })
    }
  }
}
