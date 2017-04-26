package io.finch.internal

import com.twitter.util.Future
import io.catbird.util.Rerunnable
import io.finch._
import shapeless.HNil

/**
 * Predefined, Finch-specific instances of [[Rerunnable]].
 */
private[finch] object Rs {

  private[this] val noneInstance: Rerunnable[Output[Option[Nothing]]] =
    new Rerunnable[Output[Option[Nothing]]] {
      override val run: Future[Output[Option[Nothing]]] = Future.value(Output.None)
    }

  private[this] val bodyNotPresentInstance: Rerunnable[Output[Nothing]] =
    new Rerunnable[Output[Nothing]] {
      override val run: Future[Output[Nothing]] = Future.exception(Error.NotPresent(items.BodyItem))
    }

  final val OutputHNil: Rerunnable[Output[HNil]] =
    new Rerunnable[Output[HNil]] {
      override val run: Future[Output[HNil]] = Future.value(Output.payload(HNil))
    }

  final def none[A]: Rerunnable[Output[Option[A]]] =
    noneInstance.asInstanceOf[Rerunnable[Output[Option[A]]]]

  final def bodyNotPresent[A]: Rerunnable[Output[A]] =
    bodyNotPresentInstance.asInstanceOf[Rerunnable[Output[A]]]

  final def payload[A](a: => A): Rerunnable[Output[A]] = Rerunnable(Output.payload(a))

  final def constFuture[A](fa: Future[A]): Rerunnable[A] = new Rerunnable[A] {
    override def run: Future[A] = fa
  }
}
