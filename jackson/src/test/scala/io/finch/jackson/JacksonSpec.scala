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
 * Contributor(s): -
 */
package io.finch.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.finch.test.json.JsonCodecProviderProperties
import org.scalatest.prop.Checkers
import org.scalatest.{Matchers, FlatSpec}

class JacksonSpec extends FlatSpec with Matchers with Checkers with JsonCodecProviderProperties {

  implicit val objectMapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  "The Jackson codec provider" should "encode a case class as JSON" in encodeNestedCaseClass
  it should "decode a case class from JSON" in decodeNestedCaseClass
  it should "properly fail to decode invalid JSON into a case class" in failToDecodeInvalidJson
  it should "encode a list of case class instances as JSON" in encodeCaseClassList
  it should "provide encoders with the correct content type" in checkContentType
}
