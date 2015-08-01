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
import shapeless.HNil

/**
 * An universal [[Router]] that matches the given string.
 */
private[route] class Matcher(s: String) extends Router[HNil] {
  import Router._
  def apply(input: Input): Option[(Input, () => Future[HNil])] =
    input.headOption.collect({ case `s` => () => Future.value(HNil: HNil) }).map((input.drop(1), _))

  override def toString: String = s
}

/**
 * A [[Router]] that skips all path parts.
 */
object * extends Router[HNil] {
  import Router._
  def apply(input: Input): Option[(Input, () => Future[HNil])] =
    Some((input.copy(path = Nil), () => Future.value(HNil)))

  override def toString: String = "*"
}

/**
 * An identity [[Router]].
 */
object / extends Router[HNil] {
  import Router._
  def apply(input: Input): Option[(Input, () => Future[HNil])] =
    Some((input, () => Future.value(HNil)))

  override def toString: String = ""
}
