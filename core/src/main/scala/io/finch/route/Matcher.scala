/*
 * Copyright 2015, by Vladimir Kostyukov and Contributors.
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

package io.finch.route

import com.twitter.finagle.httpx.Method
import io.finch.route.tokens._

/**
 * A universal matcher.
 */
case class Matcher(s: String) extends Router0 {
  def matchRoute(route: Route): Option[Route] = for {
    PathToken(ss) <- route.headOption if ss == s
  } yield route.tail
  override def toString = s
}

/**
 * A [[io.finch.route.Router0 Router0]] that matches the given HTTP method `m` in the route.
 */
case class MethodMatcher(m: Method) extends Router0 {
  def matchRoute(route: Route): Option[Route] = for {
    MethodToken(mm) <- route.headOption if m == mm
  } yield route.tail
  override def toString = s"${m.toString.toUpperCase}"
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
