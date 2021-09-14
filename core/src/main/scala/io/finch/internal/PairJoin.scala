package io.finch.internal

import scala.Tuple.Concat

/**
 * Compile two values together such that:
 *   - if both arguments are non-empty tuples, they're concatenated into a bigger tuple
 *   - if one of the arguments is a non-empty tuple, one prepends the other (a|b *: b|a)
 *   - if one of the arguments is an empty tuple, the second argument is returned
 */
trait PairAdjoin[A, B] {
  type Out

  def apply(a: A, b: B): Out
}

object PairAdjoin extends LowPriorityAdjoin {

  type Aux[A, B, O] = PairAdjoin[A, B] { type Out = O }

  given emptyAdjoin: PairAdjoin.Aux[EmptyTuple, EmptyTuple, EmptyTuple] = new PairAdjoin[EmptyTuple, EmptyTuple] {
    type Out = EmptyTuple

    def apply(a: EmptyTuple, b: EmptyTuple): Out = EmptyTuple
  }

  given emptyLeftAdjoin[B]: PairAdjoin.Aux[EmptyTuple, B, B] = new PairAdjoin[EmptyTuple, B] {
    type Out = B

    def apply(a: EmptyTuple, b: B): Out = b
  }

  given emptyRightAdjoin[A]: PairAdjoin.Aux[A, EmptyTuple, A] = new PairAdjoin[A, EmptyTuple] {
    type Out = A

    def apply(a: A, b: EmptyTuple): Out = a
  }

  given tuplesAdjoin[A <: Tuple, B <: Tuple]: PairAdjoin.Aux[A, B, Concat[A, B]] = new PairAdjoin[A, B] {
    type Out = Concat[A, B]

    def apply(a: A, b: B): Out = a ++ b
  }

}

private[finch] trait LowPriorityAdjoin extends LowLowPriorityAdjoin {
  given rightAdjoin[A <: Tuple, B]: PairAdjoin.Aux[A, B, Concat[A, B *: EmptyTuple]] = new PairAdjoin[A, B] {
    type Out = Concat[A, B *: EmptyTuple]

    def apply(a: A, b: B): Out = a ++ (b *: EmptyTuple)
  }

  given leftAdjoin[A, B <: Tuple]: PairAdjoin.Aux[A, B, A *: B] = new PairAdjoin[A, B] {
    type Out = A *: B

    def apply(a: A, b: B): Out = a *: b
  }
}

private[finch] trait LowLowPriorityAdjoin {
  given constructAdjoin[A, B]: PairAdjoin.Aux[A, B, A *: B *: EmptyTuple] = new PairAdjoin[A, B] {
    type Out = A *: B *: EmptyTuple

    def apply(a: A, b: B): Out = a *: b *: EmptyTuple
  }
}
