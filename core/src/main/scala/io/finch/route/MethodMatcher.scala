package io.finch.route

import com.twitter.util.Future
import com.twitter.finagle.httpx.Method
import shapeless.HNil

/**
 * A [[Router]] that matches the given HTTP method.
 */
private[route] class MethodMatcher(m: Method) extends Router[HNil] {
  import Router._
  def apply(input: Input): Future[Output[HNil]] =
    if (input.request.method == m) Future.value(Output.accepted[HNil](input, HNil))
    else Future.value(Output.dropped[HNil])

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
