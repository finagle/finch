package io.finch.syntax

import cats.effect.Effect
import com.twitter.finagle.http.Method
import io.finch._

class EndpointMappers[F[_]](implicit effect: Effect[F]) {

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `GET` and the underlying
   * endpoint succeeds on it.
   */
  def get[A](e: Endpoint[F, A]): EndpointMapper[F, A] = new EndpointMapper[F, A](Method.Get, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `POST` and the underlying
   * endpoint succeeds on it.
   */
  def post[A](e: Endpoint[F, A]): EndpointMapper[F, A] = new EndpointMapper[F, A](Method.Post, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `PATCH` and the underlying
   * endpoint succeeds on it.
   */
  def patch[A](e: Endpoint[F, A]): EndpointMapper[F, A] = new EndpointMapper[F, A](Method.Patch, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `DELETE` and the
   * underlying endpoint succeeds on it.
   */
  def delete[A](e: Endpoint[F, A]): EndpointMapper[F, A] = new EndpointMapper[F, A](Method.Delete, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `HEAD` and the underlying
   * endpoint succeeds on it.
   */
  def head[A](e: Endpoint[F, A]): EndpointMapper[F, A] = new EndpointMapper[F, A](Method.Head, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `OPTIONS` and the
   * underlying endpoint succeeds on it.
   */
  def options[A](e: Endpoint[F, A]): EndpointMapper[F, A] = new EndpointMapper[F, A](Method.Options, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `PUT` and the underlying
   * endpoint succeeds on it.
   */
  def put[A](e: Endpoint[F, A]): EndpointMapper[F, A] = new EndpointMapper[F, A](Method.Put, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `CONNECT` and the
   * underlying endpoint succeeds on it.
   */
  def connect[A](e: Endpoint[F, A]): EndpointMapper[F, A] = new EndpointMapper[F, A](Method.Connect, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `TRACE` and the underlying
   * router endpoint on it.
   */
  def trace[A](e: Endpoint[F, A]): EndpointMapper[F, A] = new EndpointMapper[F, A](Method.Trace, e)
}
