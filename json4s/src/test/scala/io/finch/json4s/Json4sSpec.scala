package io.finch.json4s

import io.finch.test.AbstractJsonSpec
import org.json4s.DefaultFormats

class Json4sSpec extends AbstractJsonSpec {

  implicit val formats = DefaultFormats

  checkJson("json4s")
}
