/*
 * Copyright 2015 Vladimir Kostyukov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.finch.route

import com.twitter.util.Future
import com.twitter.finagle.httpx.Method
import shapeless.HNil

/**
 * A [[Router]] that matches the given HTTP method.
 */
private[route] class MethodMatcher(m: Method) extends Router[HNil] {
  import Router._
  def apply(input: Input): Option[(Input, () => Future[HNil])] =
    if (input.request.method == m) Some((input, () => Future.value(HNil)))
    else None

  override def toString: String = s"${m.toString.toUpperCase}"
}

//
// A group of routers that matches HTTP methods.
//
object Get extends MethodMatcher(Method.Get)
object Post extends MethodMatcher(Method.Post)
object Patch extends MethodMatcher(Method.Patch)
object Delete extends MethodMatcher(Method.Delete)
object Head extends MethodMatcher(Method.Head)
object Options extends MethodMatcher(Method.Options)
object Put extends MethodMatcher(Method.Put)
object Connect extends MethodMatcher(Method.Connect)
object Trace extends MethodMatcher(Method.Trace)
