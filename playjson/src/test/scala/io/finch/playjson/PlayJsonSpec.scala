package io.finch.playjson

import io.finch.test.AbstractJsonSpec
import io.finch.test.data._
import play.api.libs.json._

class PlayJsonSpec extends AbstractJsonSpec {

  implicit val exampleReads: Reads[ExampleCaseClass] =
    Json.reads[ExampleCaseClass]

  implicit val exampleWrites: Writes[ExampleCaseClass] =
    Json.writes[ExampleCaseClass]

  implicit val nestedExampleReads: Reads[ExampleNestedCaseClass] =
    Json.reads[ExampleNestedCaseClass]

  implicit val nestedExampleWrites: Writes[ExampleNestedCaseClass] =
    Json.writes[ExampleNestedCaseClass]

  checkJson("playjson")
}
