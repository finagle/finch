package io.finch.route

trait LowPriorityRouterImplicits {

  /**
   * Add `/>` compositors to `RouterN` to compose it with function of one argument.
   */
  implicit class RArrow1[A](r: RouterN[A]) {
    def />[B](fn: A => B): RouterN[B] = r.map(fn)
    def |[B >: A](that: RouterN[B]): RouterN[B] = r orElse that
  }
}
