package io.finch.endpoint

import com.twitter.finagle.http.{Method => FinagleMethod}
import com.twitter.io.Buf
import io.finch.{Endpoint, EndpointResult, Input}

private[finch] class Asset[F[_]](
  path: String,
  resource: Endpoint[F, Buf]
) extends Endpoint[F, Buf] {
  final def apply(input: Input): Endpoint.Result[F, Buf] = {
    val req = input.request
    if (req.method != FinagleMethod.Get || req.uri != path) EndpointResult.NotMatched[F]
    else resource(input)
  }

  final override def toString: String = path
}
