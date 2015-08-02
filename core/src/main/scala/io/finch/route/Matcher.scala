package io.finch.route

import com.twitter.util.Future
import shapeless.HNil

/**
 * An universal [[Router]] that matches the given string.
 */
private[route] class Matcher(s: String) extends Router[HNil] {
  import Router._
  def apply(input: Input): Option[(Input, () => Future[HNil])] =
    input.headOption.collect({ case `s` => () => Future.value(HNil: HNil) }).map((input.drop(1), _))

  override def toString: String = s
}

/**
 * A [[Router]] that skips all path parts.
 */
object * extends Router[HNil] {
  import Router._
  def apply(input: Input): Option[(Input, () => Future[HNil])] =
    Some((input.copy(path = Nil), () => Future.value(HNil)))

  override def toString: String = "*"
}

/**
 * An identity [[Router]].
 */
object / extends Router[HNil] {
  import Router._
  def apply(input: Input): Option[(Input, () => Future[HNil])] =
    Some((input, () => Future.value(HNil)))

  override def toString: String = ""
}
