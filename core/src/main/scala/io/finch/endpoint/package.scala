package io.finch

import scala.reflect.ClassTag

import io.finch.internal.Rs

package object endpoint {

  private[finch] def notParsed[A](self: Endpoint[A], input: Input, e: Throwable, tag: ClassTag[A]): Endpoint.Result[A] =
    EndpointResult.Matched(input, Rs.exception(Error.NotParsed(self.item, tag, e)))

}
