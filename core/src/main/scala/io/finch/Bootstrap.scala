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
class Bootstrap[ES <: HList, CTS <: HList](
    val endpoints: ES,
    val includeDateHeader: Boolean = true,
    val includeServerHeader: Boolean = true,
    val negotiateContentType: Boolean = false) { self =>

  class Serve[CT] {
    def apply[E](e: Endpoint[E]): Bootstrap[Endpoint[E] :: ES, CT :: CTS] =
      new Bootstrap[Endpoint[E] :: ES, CT :: CTS](
        e :: self.endpoints, includeDateHeader, includeServerHeader
      )
    }

  def configure(
    includeDateHeader: Boolean = self.includeDateHeader,
    includeServerHeader: Boolean = self.includeServerHeader,
    negotiateContentType: Boolean = self.negotiateContentType
  ): Bootstrap[ES, CTS] =
    new Bootstrap[ES, CTS](endpoints, includeDateHeader, includeServerHeader, negotiateContentType)

  def serve[CT]: Serve[CT] = new Serve[CT]

  def toService(implicit ts: ToService[ES, CTS]): Service[Request, Response] =
    ts(endpoints, includeDateHeader, includeServerHeader, negotiateContentType)

  final override def toString: String = s"Bootstrap($endpoints)"
}

object Bootstrap extends Bootstrap[HNil, HNil](
    endpoints = HNil,
    includeDateHeader = true,
    includeServerHeader = true,
    negotiateContentType = false
)
