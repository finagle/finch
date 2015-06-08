package io.finch.route

import shapeless._, ops.hlist.Prepend

/**
 * This is a general-purpose type class that allows two values to be combined in
 * such a way that if both are hlists, the result is their concatenation, if
 * either is an hlist, the result is an hlist with the other either prepended or
 * appended (as appropriate), and if neither is an hlist, the result is an hlist
 * containing the two values.
 */
trait Smash[A, B] extends DepFn2[A, B]

trait LowPrioritySmash {
  type Aux[A, B, Out0] = Smash[A, B] { type Out = Out0 }

  implicit def smashII[A, B]: Aux[A, B, A :: B :: HNil] = new Smash[A, B] {
    type Out = A :: B :: HNil
    def apply(a: A, b: B): Out = a :: b :: HNil
  }
}

trait MidPrioritySmash extends LowPrioritySmash {
  implicit def smashIL[A, B <: HList]: Aux[A, B, A :: B] = new Smash[A, B] {
    type Out = A :: B
    def apply(a: A, b: B): Out = a :: b
  }

  implicit def smashLI[A <: HList, B](implicit
    prepend: Prepend[A, B :: HNil]
  ): Aux[A, B, prepend.Out] = new Smash[A, B] {
    type Out = prepend.Out
    def apply(a: A, b: B): Out = prepend(a, b :: HNil)
  }
}

object Smash extends MidPrioritySmash {
  def apply[A, B](implicit smash: Smash[A, B]): Aux[A, B, smash.Out] = smash

  def smash[A, B](a: A, b: B)(implicit smash: Smash[A, B]): smash.Out =
    smash(a, b)

  implicit def smashLL[A <: HList, B <: HList](implicit
    prepend: Prepend[A, B]
  ): Aux[A, B, prepend.Out] = new Smash[A, B] {
    type Out = prepend.Out
    def apply(a: A, b: B): Out = prepend(a, b)
  }
}
