package io.finch.syntax

import com.twitter.finagle.http.Method
import io.finch._

private[finch] trait EndpointMappers {

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `GET` and the underlying
   * endpoint succeeds on it.
   */
  def get[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Get, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `POST` and the underlying
   * endpoint succeeds on it.
   */
  def post[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Post, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `PATCH` and the underlying
   * endpoint succeeds on it.
   */
  def patch[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Patch, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `DELETE` and the
   * underlying endpoint succeeds on it.
   */
  def delete[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Delete, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `HEAD` and the underlying
   * endpoint succeeds on it.
   */
  def head[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Head, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `OPTIONS` and the
   * underlying endpoint succeeds on it.
   */
  def options[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Options, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `PUT` and the underlying
   * endpoint succeeds on it.
   */
  def put[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Put, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `CONNECT` and the
   * underlying endpoint succeeds on it.
   */
  def connect[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Connect, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `TRACE` and the underlying
   * router endpoint on it.
   */
  def trace[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Trace, e)
}
