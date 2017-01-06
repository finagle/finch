package io.finch.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala._
import com.fasterxml.jackson.module.scala.experimental._
import io.finch.test.AbstractJsonSpec

class JacksonSpec extends AbstractJsonSpec {

  // See https://github.com/FasterXML/jackson-module-scala/issues/187
  implicit val objectMapper: ObjectMapper with ScalaObjectMapper = {
    val mapper = new ObjectMapper with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    mapper
  }

  checkJson("jackson")
}
