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
