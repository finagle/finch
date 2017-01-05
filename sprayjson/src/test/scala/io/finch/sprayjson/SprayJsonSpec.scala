package io.finch.sprayjson

import io.finch.test.AbstractJsonSpec
import io.finch.test.data._
import spray.json._

class SprayJsonSpec extends AbstractJsonSpec {

  import DefaultJsonProtocol._

  implicit val exampleFormat: JsonFormat[ExampleCaseClass] = jsonFormat3(ExampleCaseClass.apply)
  implicit val nestedExampleFormat: JsonFormat[ExampleNestedCaseClass] = jsonFormat5(ExampleNestedCaseClass.apply)

  checkJson("sprayjson")
}
