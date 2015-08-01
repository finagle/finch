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

package io.finch.request

import scala.annotation.implicitNotFound

/**
 * A reasonable and safe approach to implicit view `A => B`.
 */
@implicitNotFound("Can not view ${A} as ${B}. You must define an implicit value of type View[${A}, ${B}].")
trait View[A, B] {
  def apply(x: A): B
}

/**
 * A companion object for [[View]].
 */
object View {
  def apply[A, B](f: A => B): View[A, B] = new View[A, B] {
    def apply(x: A): B = f(x)
  }

  implicit def identityView[A]: View[A, A] = View(x => x)
}
