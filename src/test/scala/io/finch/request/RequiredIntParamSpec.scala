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
 * Ryan Plessner
 */

package io.finch.request

import com.twitter.finagle.http.Request
import com.twitter.util.{Await, Future}
import io.finch._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class RequiredIntParamSpec extends FlatSpec {

  "A RequiredIntParam" should "be parsed as an integer" in {
    val request: HttpRequest = Request.apply(("foo", "5"))
    val futureResult: Future[Int] = RequiredIntParam("foo")(request)
    Await.result(futureResult) should equal(5)
  }

  it should "produce an error if the param is not a number" in {
    val request: HttpRequest = Request.apply(("foo", "non-number"))
    val futureResult: Future[Int] = RequiredIntParam("foo")(request)
    intercept[ValidationFailed] {
      Await.result(futureResult)
    }
  }
}
