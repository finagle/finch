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

import com.twitter.util.Try

/**
 * An universal extractor that extracts some value of type `A` if it's possible to fetch the value from the string.
 */
case class Extractor[A](name: String, f: String => A) extends Router[A] {
  def apply(input: RouterInput): Option[(RouterInput, A)] = for {
    ss <- input.headOption
    a <- Try(f(ss)).toOption
  } yield (input.drop(1), a)

  def apply(n: String): Extractor[A] = copy[A](name = n)
  override def toString = s":$name"
}

/**
 * A [[io.finch.route.Router Router]] that extract an integer from the route.
 */
object int extends Extractor("int", _.toInt)

/**
 * A [[io.finch.route.Router Router]] that extract a long value from the route.
 */
object long extends Extractor("long", _.toLong)

/**
 * A [[io.finch.route.Router Router]] that extract a string value from the route.
 */
object string extends Extractor("string", identity)

/**
 * A [[io.finch.route.Router Router]] that extract a boolean value from the route.
 */
object boolean extends Extractor("boolean", _.toBoolean)
