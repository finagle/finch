package io.finch.route.tokens

import com.twitter.finagle.httpx.Method

/**
 * ADT that describes a route abstraction.
 */
private[route] sealed trait RouteToken
private[route] case class MethodToken(m: Method) extends RouteToken
private[route] case class PathToken(p: String) extends RouteToken
