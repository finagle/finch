package io.finch.playjson

import play.api.libs.json._
case class Bar(x: Int, y: Boolean)

object Bar {
  implicit val barWrites = Json.writes[Bar]
  implicit val barReads = Json.reads[Bar]
}
