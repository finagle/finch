package io.finch.json

import org.scalatest.{Matchers, FlatSpec}

class JsonSpec extends FlatSpec with Matchers {

  def unwrapObject(j: Json): Map[String, Any] = j match {
    case JsonObject(outer) => outer.map {
      case (k, inner: JsonObject) => k -> unwrapObject(inner)
      case (k, v) => k -> v
    }
    case _ => Map.empty[String, Any]
  }

  def unwrapArray(j: Json): List[Any] = j match {
    case JsonArray(list) => list
    case _ => List.empty[Any]
  }

  "A JSON API" should "support JSON AST construction" in {
    val map = unwrapObject(Json.obj("a.b.c" -> 1, "a.b.d" -> 2))
    val list = unwrapArray(Json.arr("a", "b", "c"))

    map shouldBe Map("a" -> Map("b" -> Map("c" -> 1, "d" -> 2)))
    list shouldBe List("a", "b", "c")
  }
}
