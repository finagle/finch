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

  it should "create empty object/array" in {
    val map = unwrapObject(Json.emptyObject)
    val list = unwrapArray(Json.emptyObject)

    map shouldBe empty
    list shouldBe empty
  }

  it should "support JSON to string encoding" in {
    val a = Json.obj("a.b" -> 1, "a.c" -> 2, "a.d.e" -> 3)
    val b = Json.obj("a" -> 1)
    val c = Json.arr("1", 2, Json.obj("3" -> 3))
    val d = JsonNull

    Json.encode(a) shouldBe "{a:{b:1,c:2,d:{e:3}}}"
    Json.encode(b) shouldBe "{a:1}"
    Json.encode(c) shouldBe "[1,2,{3:3}]"
    Json.encode(d) shouldBe "null"
  }

  it should "support JSON from string decoding" in {
    Json.decode("{\"a\":1,\"b\":[1,2,3]}") shouldBe Json.obj("a" -> 1, "b" -> Json.arr(1, 2, 3))
    Json.decode("[\"a\",\"b\",{\"c\":1}]") shouldBe Json.arr("a", "b", Json.obj("c" -> 1))
  }

  it should "compress objects" in {
    val a = Json.obj("a.b" -> null, "a.c" -> null, "a.d" -> 1)
    val map = unwrapObject(Json.compress(a))

    map shouldBe Map("a" -> Map("d" -> 1))
  }

  it should "compress arrays" in {
    val a = Json.arr(Json.obj("a" -> null, "a.b" -> 1, "a.c" -> null))
    val list = unwrapArray(Json.compress(a)) map {
      case j: Json => unwrapObject(j)
      case other => other
    }

    list shouldBe List(Map("a" -> Map("b" -> 1)))
  }

  it should "concat objects" in {
    val a = Json.obj("a.b.c" -> 1)
    val b = Json.obj("a.b.d" -> 2)
    val map = unwrapObject(Json.concatLeft(a, b))

    map shouldBe Map("a" -> Map("b" -> Map("d" -> 2)))
  }

  it should "concat arrays" in {
    val a = Json.arr(1, 2, 3)
    val b = Json.arr(4, 5, 6)
    val list = unwrapArray(Json.concatLeft(a, b))

    list shouldBe List(1, 2, 3, 4, 5, 6)
  }

  it should "concat arrays/objects with nulls" in {
    val a = Json.obj("a" -> 1)
    val b = Json.arr(1)

    Json.concatLeft(a, JsonNull) shouldBe a
    Json.concatRight(b, JsonNull) shouldBe b
  }

  it should "produce an error when concat object with arrays" in {
    val a = Json.obj("a" -> 1)
    val b = Json.arr(1)

    an [IllegalArgumentException] should be thrownBy Json.concatLeft(a, b)
  }

  it should "merge objects" in {
    val a = Json.obj("a.b.c" -> 1, "a.c" -> 2)
    val b = Json.obj("a.b.d" -> 3, "a.c.e" -> 4)
    val c = Json.obj("a.c" -> 5, "a.b" -> 6)

    unwrapObject(Json.mergeLeft(a, b)) shouldBe Map("a" -> Map("b" -> Map("c" -> 1, "d" -> 3), "c" -> 2))
    unwrapObject(Json.mergeRight(b, c)) shouldBe Map("a" -> Map("b" -> 6, "c" -> 5))
  }

  it should "merge object with nulls" in {
    val a = Json.obj("a" -> 1)

    Json.mergeLeft(a, JsonNull) shouldBe a
  }

  it should "produce an error when merge arrays" in {
    val a = Json.arr(1, 2, 3)
    val b = Json.arr(1, 2, 3)

    an [IllegalArgumentException] should be thrownBy Json.mergeLeft(a, b)
  }

  it should "query properties" in {
    val a = Json.obj("a.b.c" -> 10, "a.d" -> Json.arr(1, 2, 3))

    a.query[Json]("a.b").map(unwrapObject) shouldBe Some(Map("c" -> 10))
    a.query[Int]("a.b.c") shouldBe Some(10)
    a.query[Json]("a.d").map(unwrapArray) shouldBe Some(List(1, 2, 3))
  }
}
