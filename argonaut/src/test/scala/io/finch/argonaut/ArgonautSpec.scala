package io.finch.argonaut

import argonaut.Argonaut._
import argonaut._
import io.finch.test.AbstractJsonSpec
import io.finch.test.data._

class ArgonautSpec extends AbstractJsonSpec {

  implicit val exampleCaseClassCodecJson: CodecJson[ExampleCaseClass] =
    casecodec3(ExampleCaseClass.apply, ExampleCaseClass.unapply)("a", "b", "c")

  implicit val exampleNestedCaseClassCodecJson: CodecJson[ExampleNestedCaseClass] =
    casecodec5(ExampleNestedCaseClass.apply, ExampleNestedCaseClass.unapply)(
      "string",
      "double",
      "long",
      "ints",
      "example"
    )

  checkJson("argonaut")
}
