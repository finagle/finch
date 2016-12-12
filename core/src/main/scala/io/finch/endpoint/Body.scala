package io.finch.endpoint

import com.twitter.util.{Future, Return, Throw}
import io.catbird.util.Rerunnable
import io.finch._
import io.finch.internal._
import io.finch.items._
import scala.reflect.ClassTag

private[finch] abstract class Body[A, B, CT <: String](
    d: Decode.Aux[A, CT], ct: ClassTag[A]) extends Endpoint[B] {

  protected def whenNotPresent: Rerunnable[Output[B]]
  protected def prepare(a: A): B

  private[this] def decode(i: Input): Future[Output[B]] =
    d(i.request.content, i.request.charsetOrUtf8) match {
      case Return(r) => Future.value(Output.payload(prepare(r)))
      case Throw(t) => Future.exception(Error.NotParsed(items.BodyItem, ct, t))
    }

  final def apply(input: Input): Endpoint.Result[B] =
    if (input.request.isChunked) None
    else {
      val rr = input.request.contentLength match {
        case None => whenNotPresent
        case _ => Rerunnable.fromFuture(decode(input))
      }

      Some(input -> rr)
    }

  override def item: RequestItem = items.BodyItem
  override def toString: String = "body"
}

private[finch] final class RequiredBody[A, CT <: String](
    d: Decode.Aux[A, CT], ct: ClassTag[A]) extends Body[A, A, CT](d, ct) {

  protected def prepare(a: A): A = a
  protected def whenNotPresent: Rerunnable[Output[A]] =
    Rs.BodyNotPresent.asInstanceOf[Rerunnable[Output[A]]]
}

private[finch] final class OptionalBody[A, CT <: String](
    d: Decode.Aux[A, CT], ct: ClassTag[A]) extends Body[A, Option[A], CT](d, ct) {

  protected def prepare(a: A): Option[A] = Some(a)
  protected def whenNotPresent: Rerunnable[Output[Option[A]]] =
    Rs.OutputNone.asInstanceOf[Rerunnable[Output[Option[A]]]]
}

