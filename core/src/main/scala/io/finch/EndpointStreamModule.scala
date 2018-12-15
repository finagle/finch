package io.finch

import cats.effect.Effect
import com.twitter.io.Buf
import io.finch.streaming.{DecodeStream, StreamFromReader}

trait EndpointStreamModule[S[_[_], _], F[_]] extends EndpointModule[F] {

  /**
    * An alias for [[Endpoint.streamBinaryBody]]
    */
  def streamBinaryBody[A, CT <: String](implicit
    fromReader: StreamFromReader[S, F],
    F: Effect[F]
  ): Endpoint[F, S[F, Buf]] = Endpoint.streamBinaryBody[F, S]

  /**
    * An alias for [[Endpoint.streamJsonBody]]
    */
  def streamJsonBody[A](implicit
    decoder: DecodeStream.Aux[S, F, A, Application.Json],
    fromReader: StreamFromReader[S, F],
    F: Effect[F]
  ): Endpoint[F, S[F, A]] = Endpoint.streamJsonBody[F, S, A]

}
