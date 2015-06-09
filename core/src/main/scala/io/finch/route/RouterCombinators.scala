package io.finch.route

import com.twitter.finagle.httpx.Method

/**
 * A collection of [[Router]] combinators.
 */
trait RouterCombinators {

  private def method[A](m: Method)(r: Router[A]): Router[A] = new Router[A] {
    def apply(input: RouterInput): Option[(RouterInput, A)] =
      if (input.request.method == m) r(input) else None
    override def toString: String = s"${m.toString.toUpperCase} /${r.toString}"
  }

  /**
   * A combinator that wraps the given [[Router]] with additional check of the HTTP method. The resulting [[Router]]
   * succeeds on the request only if its method is `GET` and the underlying router succeeds on it.
   */
  def get[A]: Router[A] => Router[A] = method(Method.Get)

  /**
   * A combinator that wraps the given [[Router]] with additional check of the HTTP method. The resulting [[Router]]
   * succeeds on the request only if its method is `POST` and the underlying router succeeds on it.
   */
  def post[A]: Router[A] => Router[A] = method(Method.Post)

  /**
   * A combinator that wraps the given [[Router]] with additional check of the HTTP method. The resulting [[Router]]
   * succeeds on the request only if its method is `PATCH` and the underlying router succeeds on it.
   */
  def patch[A]: Router[A] => Router[A] = method(Method.Patch)

  /**
   * A combinator that wraps the given [[Router]] with additional check of the HTTP method. The resulting [[Router]]
   * succeeds on the request only if its method is `DELETE` and the underlying router succeeds on it.
   */
  def delete[A]: Router[A] => Router[A] = method(Method.Delete)

  /**
   * A combinator that wraps the given [[Router]] with additional check of the HTTP method. The resulting [[Router]]
   * succeeds on the request only if its method is `HEAD` and the underlying router succeeds on it.
   */
  def head[A]: Router[A] => Router[A] = method(Method.Head)

  /**
   * A combinator that wraps the given [[Router]] with additional check of the HTTP method. The resulting [[Router]]
   * succeeds on the request only if its method is `OPTIONS` and the underlying router succeeds on it.
   */
  def options[A]: Router[A] => Router[A] = method(Method.Options)

  /**
   * A combinator that wraps the given [[Router]] with additional check of the HTTP method. The resulting [[Router]]
   * succeeds on the request only if its method is `PUT` and the underlying router succeeds on it.
   */
  def put[A]: Router[A] => Router[A] = method(Method.Put)

  /**
   * A combinator that wraps the given [[Router]] with additional check of the HTTP method. The resulting [[Router]]
   * succeeds on the request only if its method is `CONNECT` and the underlying router succeeds on it.
   */
  def connect[A]: Router[A] => Router[A] = method(Method.Connect)

  /**
   * A combinator that wraps the given [[Router]] with additional check of the HTTP method. The resulting [[Router]]
   * succeeds on the request only if its method is `TRACE` and the underlying router succeeds on it.
   */
  def trace[A]: Router[A] => Router[A] = method(Method.Trace)
}
