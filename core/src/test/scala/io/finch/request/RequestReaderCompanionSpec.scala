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
 * Contributor(s):
 * Jens Halm
 */
package io.finch.request

import com.twitter.finagle.httpx.Request
import com.twitter.util.{Await, Future}
import io.finch._
import org.scalatest.{Matchers, FlatSpec}
import items._

class RequestReaderCompanionSpec extends FlatSpec with Matchers {

  "The RequestReaderCompanion" should "support a facotry method based on a funciton that reads from the request" in {
    val request: HttpRequest = Request(("foo", "5"))
    val futureResult: Future[String] = RequestReader(ParamItem("foo"))(_.params.get("foo")).failIfEmpty(request)
    Await.result(futureResult) shouldBe "5"
  }

  it should "support a factory method based on a constant Future" in {
    val request: HttpRequest = Request(("foo", ""))
    val futureResult: Future[Int] = RequestReader.const(1.toFuture)(request)
    Await.result(futureResult) shouldBe 1
  }
  
  it should "support a factory method based on a constant value" in {
    val request: HttpRequest = Request(("foo", ""))
    val futureResult: Future[Int] = RequestReader.value(1)(request)
    Await.result(futureResult) shouldBe 1
  }
  
  it should "support a factory method based on a constant exception" in {
    val request: HttpRequest = Request(("foo", ""))
    val futureResult: Future[Int] = RequestReader.exception(NotPresent(BodyItem))(request)
    a [NotPresent] shouldBe thrownBy(Await.result(futureResult))
  }
  
}
