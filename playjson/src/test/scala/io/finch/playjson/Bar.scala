package io.finch.playjson

import play.api.libs.json._
case class Bar(x: Int, y: Boolean)

object Bar {
  implicit val barWrites: Writes[Bar] = Json.writes[Bar]
  implicit val barReads: Reads[Bar] = Json.reads[Bar]
}
