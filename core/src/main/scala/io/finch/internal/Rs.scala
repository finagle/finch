package io.finch.internal

import com.twitter.util.Future
import io.catbird.util.Rerunnable
import io.finch._
import io.finch.items._
import shapeless.HNil

/**
 * Predefined, Finch-specific instances of [[Rerunnable]].
 */
private[finch] object Rs {

  final def constFuture[A](fa: Future[A]): Rerunnable[A] = new Rerunnable[A] {
    override def run: Future[A] = fa
  }

  // See https://github.com/travisbrown/catbird/pull/32
  final def const[A](a: A): Rerunnable[A] = constFuture(Future.value(a))

  final val OutputNone: Rerunnable[Output[Option[Nothing]]] =
    new Rerunnable[Output[Option[Nothing]]] {
      override val run: Future[Output[Option[Nothing]]] =
        Future.value(Output.None)
    }

  final val BodyNotPresent: Rerunnable[Nothing] = new Rerunnable[Nothing] {
    override val run: Future[Nothing] =
      Future.exception(Error.NotPresent(BodyItem))
  }

  final val OutputHNil: Rerunnable[Output[HNil]] =
    new Rerunnable[Output[HNil]] {
      override val run: Future[Output[HNil]] = Future.value(Output.payload(HNil))
    }
}
