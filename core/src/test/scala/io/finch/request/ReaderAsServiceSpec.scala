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

import org.scalatest.{Matchers, FlatSpec}
import com.twitter.util.{Future, Await}
import com.twitter.finagle.httpx.Request
import com.twitter.finagle.Service
import io.finch.HttpRequest

class ReaderAsServiceSpec extends FlatSpec with Matchers {

  
  "A RequestReader" should "be usable as a Finagle Service" in {
    case class MyRequest(request: HttpRequest)
    
    implicit def extractRequest(req: MyRequest): HttpRequest = req.request
    
    val request: MyRequest = MyRequest(Request(("foo", "5")))
    val reader: RequestReader[Int] = RequiredParam("foo").as[Int]
    val service: Service[MyRequest, Int] = reader.asService[MyRequest]
    val result = service(request)
    Await.result(result) shouldBe 5
  }
  
}
