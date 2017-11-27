package io.finch

import scala.reflect.ClassTag

import io.finch.internal.Rs

package object endpoint {

  private[finch] def notParsed[F[_], A](self: Endpoint[F[A]],
                                        input: Input, e: Throwable,
                                        tag: ClassTag[A]
                                       ): Endpoint.Result[F[A]] =
    EndpointResult.Matched[F[A]](input, Rs.exception(Error.NotParsed(self.item, tag, e)))

}
