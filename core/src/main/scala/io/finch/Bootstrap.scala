package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import shapeless._

/**
 * Bootstraps a Finagle HTTP service out of the collection of Finch endpoints.
 *
 * {{{
 * val api: Service[Request, Response] = Bootstrap
 *  .serve[Application.Json](getUser :+: postUser)
 *  .serve[Text.Plain](healthcheck)
 *  .toService
 * }}}
 *
 * @note This API is experimental/unstable. Use with caution.
 */
case class Bootstrap[ES <: HList, CTS <: HList](
    endpoints: ES,
    includeDateHeader: Boolean = true,
    includeServerHeader: Boolean = true) { self =>

  class Serve[CT <: String] {
    def apply[E](e: Endpoint[E]): Bootstrap[Endpoint[E] :: ES, CT :: CTS] =
      self.copy(endpoints = e :: self.endpoints)
    }

  def serve[CT <: String]: Serve[CT] = new Serve[CT]

  def toService(implicit ts: ToService[ES, CTS]): Service[Request, Response] =
    ts(endpoints, includeDateHeader, includeServerHeader)
}

object Bootstrap extends Bootstrap[HNil, HNil](
    endpoints = HNil,
    includeDateHeader = true,
    includeServerHeader = true)
