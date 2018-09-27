package io.finch

import io.finch.syntax.EndpointMappers

trait Module[F[_]] extends Endpoints[F]
  with EndpointMappers[F]
  with Outputs
  with ValidationRules {

  type Endpoint[A] = io.finch.Endpoint[F, A]

}
