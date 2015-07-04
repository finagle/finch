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
 * Pedro Viegas
 */

package io.finch

/**
 * This package enables a reasonable approach of building HTTP responses using the
 * [[io.finch.response.ResponseBuilder ResponseBuilder]] abstraction. The `ResponseBuilder` provides an immutable way
 * of building concrete [[com.twitter.finagle.httpx.Response Response]] instances by specifying their ''status'',
 * ''headers'' and ''cookies''. There are plenty of predefined builders named by HTTP statuses, i.e., `Ok`, `Created`,
 * `NotFound`. Thus, the typical use case of the `ResponseBuilder` abstraction involves usage of concrete builder
 * instead of abstract `ResponseBuilder` itself.
 *
 * {{{
 *   val ok: Response = Ok("Hello, World!")
 * }}}
 *
 * In addition to `text/plain` responses, the `ResponseBuilder` is able to build any response, whose `content-type` is
 * specified by an implicit type-class [[io.finch.response.EncodeResponse EncodeResponse]] instance. In fact, any type
 * `A` may be passed to a `RequestReader` if there is a corresponding `EncodeRequest[A]` instance available in the
 * scope.
 *
 * {{{
 *   implicit val encodeBigInt = EncodeResponse[BigInt]("text/plain") { _.toString }
 *   val ok: Response = Ok(BigInt(100))
 * }}}
 */
package object response
