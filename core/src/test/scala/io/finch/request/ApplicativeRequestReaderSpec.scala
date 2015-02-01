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

import org.scalatest.{FlatSpec,Matchers}

import com.twitter.finagle.httpx.Request
import com.twitter.util.{Await,Throw,Try,Return}
import items._

class ApplicativeRequestReaderSpec extends FlatSpec with Matchers {

  val reader: RequestReader[(Int, Double, Int)] =
    (RequiredIntParam("a") ~
     RequiredDoubleParam("b") ~
     RequiredIntParam("c")) map { 
       case a ~ b ~ c => (a, b, c)
     }
  
  def extractNotParsedTargets (result: Try[(Int, Double, Int)]): AnyRef = {
    (result handle {
      case RequestReaderErrors(errors) => errors map {
        case NotParsed(item, _, _) => item
      }
      case NotParsed(item, _, _) => Seq(item)
      case _ => Seq()
    }).get
  }
  

  "The applicative reader" should "produce three errors if all three numbers cannot be parsed" in {
    val request = Request.apply("a"->"foo", "b"->"foo", "c"->"foo")
    extractNotParsedTargets(Await.result(reader(request).liftToTry)) should be (Seq(
      ParamItem("a"),
      ParamItem("b"),
      ParamItem("c")
    ))
  }
  
  it should "produce two validation errors if two numbers cannot be parsed" in {
    val request = Request.apply("a"->"foo", "b"->"7.7", "c"->"foo")
    extractNotParsedTargets(Await.result(reader(request).liftToTry)) should be (Seq(
      ParamItem("a"),
      ParamItem("c")
    ))
  }
  
  it should "produce two ParamNotFound errors if two parameters are missing" in {
    val request = Request.apply("b"->"7.7")
    Await.result(reader(request).liftToTry) should be (Throw(RequestReaderErrors(Seq(
      NotFound(ParamItem("a")),
      NotFound(ParamItem("c"))
    ))))
  }
  
  it should "produce one error if the last parameter cannot be parsed to an integer" in {
    val request = Request.apply("a"->"9", "b"->"7.7", "c"->"foo")
    extractNotParsedTargets(Await.result(reader(request).liftToTry)) should be (Seq(ParamItem("c")))
  }
  
  it should "parse all integers and doubles" in {
    val request = Request.apply("a"->"9", "b"->"7.7", "c"->"5")
    Await.result(reader(request)) should be ((9,7.7,5))
  }
}
