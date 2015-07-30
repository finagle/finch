package io.finch.route

import com.twitter.util.Future
import com.twitter.finagle.httpx.Method
import shapeless.HNil

/**
 * A [[Router]] that matches the given HTTP method.
 */
private[route] class MethodMatcher(m: Method) extends Router[HNil] {
  import Router._
  def apply(input: Input): Option[(Input, () => Future[HNil])] =
    if (input.request.method == m) Some((input, () => Future.value(HNil)))
    else None

  override def toString: String = s"${m.toString.toUpperCase}"
}

//
// A group of routers that matches HTTP methods.
//
object Get extends MethodMatcher(Method.Get)
object Post extends MethodMatcher(Method.Post)
object Patch extends MethodMatcher(Method.Patch)
object Delete extends MethodMatcher(Method.Delete)
object Head extends MethodMatcher(Method.Head)
object Options extends MethodMatcher(Method.Options)
object Put extends MethodMatcher(Method.Put)
object Connect extends MethodMatcher(Method.Connect)
object Trace extends MethodMatcher(Method.Trace)
