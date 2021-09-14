package io.finch.internal

sealed trait OrElse[A, B] {
  def fold[C](a: A => C, b: B => C): C
}

object OrElse extends LowPriorityElse {

  given left[A, B](using aa: A): OrElse[A, B] = new OrElse[A, B] {
    def fold[C](a: A => C, b: B => C): C = a(aa)
  }

}

trait LowPriorityElse {

  given right[A, B](using bb: B): OrElse[A, B] = new OrElse[A, B] {
    def fold[C](a: A => C, b: B => C): C = b(bb)
  }

}
