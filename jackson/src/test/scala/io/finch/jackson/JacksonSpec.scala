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
import com.twitter.finagle.httpx.Request
import com.twitter.io.Buf.Utf8
import com.twitter.util.{Await, Future, Return}
import io.finch.request.{OptionalBody, RequiredBody, RequestReader}
import io.finch.response.Ok
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.scalatest.{Matchers, FlatSpec}

class JacksonSpec extends FlatSpec with Matchers {
  implicit val objectMapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  "JacksonEncode" should "encode a case class into JSON" in {
    val encode = encodeJackson(objectMapper)
    encode(Foo("bar", 10)) shouldBe "{\"bar\":\"bar\",\"baz\":10}"
  }

  it should "decode a case class from JSON" in {
    val json = "{\"bar\":\"bar\",\"baz\":20}"
    val decode = decodeJackson(objectMapper)
    decode[Foo](json) shouldBe Return(Foo("bar", 20))
  }
  
  it should "fail given invalid JSON" in {
    val json = "{\"bar\":\"bar\",\"baz\":20}"
    val decode = decodeJackson(objectMapper)
    decode[Foo]("{{{{").isThrow shouldBe true
  }

  it should "work w/o exceptions with ResponseBuilder" in {
    val foo = Foo("bar", 42)
    Ok(foo).getContentString() shouldBe "{\"bar\":\"bar\",\"baz\":42}"
  }

  it should "work with higher kinded types" in {
    val list = List(1, 2, 3)
    val encode = encodeJackson(objectMapper)
    val decode = decodeJackson(objectMapper)

    encode(list) shouldBe "[1,2,3]"
    decode[List[Int]]("[1,2,3]") shouldBe Return(List(1, 2, 3))
  }

  it should "work w/o exceptions with RequestReader" in {
    val body = Utf8("{\"bar\":\"bar\",\"baz\":42}")
    val req = Request()
    req.content = body
    req.headerMap.update(HttpHeaders.Names.CONTENT_LENGTH, body.length.toString)

    val rFoo: RequestReader[Foo] = RequiredBody.as[Foo]
    val foo: Future[Foo] = RequiredBody.as[Foo].apply(req)
    val roFoo: RequestReader[Option[Foo]] = OptionalBody.as[Foo]
    val oFoo: Future[Option[Foo]] = OptionalBody.as[Foo].apply(req)

    val expectedFoo = Foo("bar", 42)
    Await.result(rFoo(req)) shouldBe expectedFoo
    Await.result(foo) shouldBe expectedFoo
    Await.result(roFoo(req)) shouldBe Some(expectedFoo)
    Await.result(oFoo) shouldBe Some(expectedFoo)
  }
}
