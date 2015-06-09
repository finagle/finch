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

import shapeless.HNil

/**
 * An universal [[Router]] that matches the given string.
 */
case class Matcher(s: String) extends Router[HNil] {
  def apply(input: RouterInput): Option[(RouterInput, HNil)] = for {
    ss <- input.headOption if ss == s
  } yield (input.drop(1), HNil)
  override def toString = s
}

/**
 * A [[Router]] that skips all path parts.
 */
object * extends Router[HNil] {
  def apply(input: RouterInput): Option[(RouterInput, HNil)] = Some((input.copy(path = Nil), HNil))
  override def toString = "*"
}

/**
 * An identity [[Router]].
 */
object / extends Router[HNil] {
  def apply(input: RouterInput): Option[(RouterInput, HNil)] = Some((input, HNil))
  override def toString = ""
}
