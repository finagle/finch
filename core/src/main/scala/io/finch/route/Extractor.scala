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

import io.finch.route.tokens.PathToken

/**
 * A [[io.finch.route.Router Router]] that extracts a path token.
 */
object PathTokenExtractor extends Router[String] {
  override def apply(route: Route): Option[(Route, String)] = for {
    PathToken(ss) <- route.headOption
  } yield (route.tail, ss)
}

/**
 * An universal extractor that extracts some value of type `A` if it's possible to fetch the value from the string.
 */
case class Extractor[A](name: String, f: String => Option[A]) extends Router[A] {
  def apply(route: Route): Option[(Route, A)] = PathTokenExtractor.embedFlatMap(f)(route)
  def apply(n: String): Extractor[A] = copy[A](name = n)
  override def toString = s":$name"
}

/**
 * A [[io.finch.route.Router Router]] that extract an integer from the route.
 */
object int extends Extractor("int", stringToSomeValue(_.toInt))

/**
 * A [[io.finch.route.Router Router]] that extract a long value from the route.
 */
object long extends Extractor("long", stringToSomeValue(_.toLong))

/**
 * A [[io.finch.route.Router Router]] that extract a string value from the route.
 */
object string extends Extractor("string", Some(_))

/**
 * A [[io.finch.route.Router Router]] that extract a boolean value from the route.
 */
object boolean extends Extractor("boolean", stringToSomeValue(_.toBoolean))
