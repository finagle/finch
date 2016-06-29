package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import io.finch.internal.ToService
import shapeless._

/**
 * Captures the `HList` of endpoints as well as the `HList` of their content-types in order to do
 * the `toService` conversion.
 *
 * {{{
 *
 * val api: Service[Request, Response] = ServiceBuilder()
 *   .respond[Application.Json](getUser :+: postUser)
 *   .respond[Text.Plain](healthcheck)
 *   .toService
 * }}}
 */
case class ServiceBuilder[ES <: HList, CTS <: HList](endpoints: ES) { self =>

  class Respond[CT <: String] {
    def apply[E](e: Endpoint[E]): ServiceBuilder[Endpoint[E] :: ES, CT :: CTS] =
      self.copy(e :: self.endpoints)
  }

  def respond[CT <: String]: Respond[CT] = new Respond[CT]

  def toService(implicit ts: ToService[ES, CTS]): Service[Request, Response] = ts(endpoints)
}

object ServiceBuilder {
  def apply(): ServiceBuilder[HNil, HNil] = ServiceBuilder(HNil)
}
