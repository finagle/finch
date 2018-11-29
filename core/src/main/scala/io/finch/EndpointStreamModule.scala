package io.finch

import cats.effect.Effect
import io.finch.streaming.{StreamDecode, StreamFromReader}

trait EndpointStreamModule[S[_[_], _], F[_]] extends EndpointModule[F] {

  /**
    * An alias for [[Endpoint.streamBody]]
    */
  def streamBody[A, CT <: String](implicit
    decoder: StreamDecode.Aux[S, F, A, CT],
    fromReader: StreamFromReader[S, F],
    F: Effect[F]
  ): Endpoint[F, S[F, A]] = Endpoint.streamBody[F, S, A, CT]

  /**
    * An alias for [[Endpoint.streamJsonBody]]
    */
  def streamJsonBody[A](implicit
    decoder: StreamDecode.Aux[S, F, A, Application.Json],
    fromReader: StreamFromReader[S, F],
    F: Effect[F]
  ): Endpoint[F, S[F, A]] = Endpoint.streamJsonBody[F, S, A]

}
