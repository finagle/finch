package io.finch.endpoint

import cats.Id
import cats.effect.Effect
import com.twitter.util.{Return, Throw, Try}
import io.finch._
import io.finch.items._
import scala.reflect.ClassTag

private[finch] class Headers[F[_] : Effect] {

  abstract class Header[G[_], A](
                                          name: String,
                                          d: DecodeEntity[A],
                                          tag: ClassTag[A]
                                        ) extends Endpoint[F, G[A]] with (Try[A] => Try[Output[G[A]]]) { self =>

    protected def missing(name: String): F[Output[G[A]]]
    protected def present(value: A): G[A]

    final def apply(ta: Try[A]): Try[Output[G[A]]] = ta match {
      case Return(a) => Return(Output.payload(present(a)))
      case Throw(e) => Throw(Error.NotParsed(items.HeaderItem(name), tag, e))
    }

    final def apply(input: Input): EndpointResult[F, G[A]] = {
      val output: F[Output[G[A]]] = Effect[F].suspend {
        input.request.headerMap.getOrNull(name) match {
          case null => missing(name)
          case value => d(value).transform(self) match {
            case Return(r) => Effect[F].pure(r)
            case Throw(t) => Effect[F].raiseError(t)
          }
        }
      }

      EndpointResult.Matched(input, Trace.empty, output)
    }

    final override def item: RequestItem = items.HeaderItem(name)
    final override def toString: String = s"header($name)"
  }

  object Header {

    private val noneInstance: F[Output[Option[Nothing]]] = Effect[F].pure(Output.None)
    private def none[A]: F[Output[Option[A]]] = noneInstance.asInstanceOf[F[Output[Option[A]]]]

    trait Required[A] { _: Header[Id, A] =>
      protected def missing(name: String): F[Output[A]] =
        Effect[F].raiseError(Error.NotPresent(items.HeaderItem(name)))
      protected def present(value: A): Id[A] = value
    }

    trait Optional[A] { _: Header[Option, A] =>
      protected def missing(name: String): F[Output[Option[A]]] = none[A]
      protected def present(value: A): Option[A] = Some(value)
    }
  }

}

trait HeaderEndpoints {

  /**
    * An evaluating [[Endpoint]] that reads a required HTTP header `name` from the request or raises
    * an [[Error.NotPresent]] exception when the header is missing.
    */
  def header[F[_] : Effect, A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[F, A] = {
    val h = new Headers[F]
    new h.Header[Id, A](name, d, tag) with h.Header.Required[A]
  }

  /**
    * An evaluating [[Endpoint]] that reads an optional HTTP header `name` from the request into an
    * `Option`.
    */
  def headerOption[F[_] : Effect, A](name: String)(implicit
                                    d: DecodeEntity[A],
                                    tag: ClassTag[A]
  ): Endpoint[F, Option[A]] = {
    val h = new Headers[F]
    new h.Header[Option, A](name, d, tag) with h.Header.Optional[A]
  }

}
