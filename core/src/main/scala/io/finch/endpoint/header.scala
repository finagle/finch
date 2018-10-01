package io.finch.endpoint

import cats.Id
import cats.effect.Effect
import com.twitter.util.{Return, Throw, Try}
import io.finch._
import io.finch.items._
import scala.reflect.ClassTag

private[finch] abstract class Header[F[_], G[_], A](name: String)(implicit
  d: DecodeEntity[A],
  tag: ClassTag[A],
  protected val F: Effect[F]
) extends Endpoint[F, G[A]] with (Try[A] => Try[Output[G[A]]]) { self =>

  protected def missing(name: String): F[Output[G[A]]]
  protected def present(value: A): G[A]

  final def apply(ta: Try[A]): Try[Output[G[A]]] = ta match {
    case Return(a) => Return(Output.payload(present(a)))
    case Throw(e) => Throw(Error.NotParsed(items.HeaderItem(name), tag, e))
  }

  final def apply(input: Input): EndpointResult[F, G[A]] = {
    val output: F[Output[G[A]]] = F.suspend {
      input.request.headerMap.getOrNull(name) match {
        case null => missing(name)
        case value => d(value).transform(self) match {
          case Return(r) => F.pure(r)
          case Throw(t) => F.raiseError(t)
        }
      }
    }

    EndpointResult.Matched(input, Trace.empty, output)
  }

  final override def item: RequestItem = items.HeaderItem(name)
  final override def toString: String = s"header($name)"
}

private[finch] object Header {

  trait Required[F[_], A] {_: Header[F, Id, A] =>
    protected def missing(name: String): F[Output[A]] =
      F.raiseError(Error.NotPresent(items.HeaderItem(name)))
    protected def present(value: A): Id[A] = value
  }

  trait Optional[F[_], A] { _: Header[F, Option, A] =>
    protected def missing(name: String): F[Output[Option[A]]] =
      F.pure(Output.None)
    protected def present(value: A): Option[A] = Some(value)
  }
}
