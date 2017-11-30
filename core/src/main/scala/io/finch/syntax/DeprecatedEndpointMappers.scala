package io.finch.syntax

import com.twitter.finagle.http.Method
import io.finch._

private[finch] trait DeprecatedEndpointMappers {
  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `GET` and the underlying
   * endpoint succeeds on it.
   */
  @deprecated("Enable syntax explicitly: import io.finch.syntax._", "0.16")
  def get[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Get, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `POST` and the underlying
   * endpoint succeeds on it.
   */
  @deprecated("Enable syntax explicitly: import io.finch.syntax._", "0.16")
  def post[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Post, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `PATCH` and the underlying
   * endpoint succeeds on it.
   */
  @deprecated("Enable syntax explicitly: import io.finch.syntax._", "0.16")
  def patch[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Patch, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `DELETE` and the
   * underlying endpoint succeeds on it.
   */
  @deprecated("Enable syntax explicitly: import io.finch.syntax._", "0.16")
  def delete[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Delete, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `HEAD` and the underlying
   * endpoint succeeds on it.
   */
  @deprecated("Enable syntax explicitly: import io.finch.syntax._", "0.16")
  def head[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Head, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `OPTIONS` and the
   * underlying endpoint succeeds on it.
   */
  @deprecated("Enable syntax explicitly: import io.finch.syntax._", "0.16")
  def options[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Options, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `PUT` and the underlying
   * endpoint succeeds on it.
   */
  @deprecated("Enable syntax explicitly: import io.finch.syntax._", "0.16")
  def put[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Put, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `CONNECT` and the
   * underlying endpoint succeeds on it.
   */
  @deprecated("Enable syntax explicitly: import io.finch.syntax._", "0.16")
  def connect[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Connect, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `TRACE` and the underlying
   * router endpoint on it.
   */
  @deprecated("Enable syntax explicitly: import io.finch.syntax._", "0.16")
  def trace[A](e: Endpoint[A]): EndpointMapper[A] = new EndpointMapper[A](Method.Trace, e)
}
