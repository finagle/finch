package io.finch

import cats.{MonoidK, SemigroupK}

private[finch] trait LowPriorityEndpointInstances {

  protected trait EndpointSemigroupK[F[_]] extends SemigroupK[Endpoint[F, *]] {
    def combineK[A](x: Endpoint[F, A], y: Endpoint[F, A]): Endpoint[F, A] =
      x.coproduct(y)
  }

  protected trait EndpointMonoidK[F[_]] extends EndpointSemigroupK[F] with MonoidK[Endpoint[F, *]] {
    def empty[A]: Endpoint[F, A] = Endpoint.empty
  }

  implicit def endpointMonoidK[F[_]]: MonoidK[Endpoint[F, *]] =
    new EndpointMonoidK[F] {}

}
