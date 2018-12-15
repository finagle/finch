package io.finch

import cats.effect.IO
import cats.laws.discipline.FunctorTests

class EndpointResultSpec extends FinchSpec {

  checkAll(
    "Functor[EndpointResult]",
    FunctorTests[EndpointResult[IO, ?]].functor[String, String, String]
  )

}
