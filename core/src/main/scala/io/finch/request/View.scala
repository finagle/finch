package io.finch.request

import scala.annotation.implicitNotFound

/**
 * A reasonable and safe approach to implicit view `A => B`.
 */
@implicitNotFound("Can not view ${A} as ${B}. You must define an implicit value of type View[${A}, ${B}].")
@deprecated(message = "Custom request are deprecated", since = "0.8.0")
trait View[A, B] {
  def apply(x: A): B
}

/**
 * A companion object for [[View]].
 */
@deprecated(message = "Custom request are deprecated", since = "0.8.0")
object View {
  def apply[A, B](f: A => B): View[A, B] = new View[A, B] {
    def apply(x: A): B = f(x)
  }

  implicit def identityView[A]: View[A, A] = View(x => x)
}
