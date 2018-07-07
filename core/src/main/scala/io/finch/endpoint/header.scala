package io.finch.endpoint

import cats.Id
import com.twitter.util.{Future, Return, Throw, Try}
import io.catbird.util.Rerunnable
import io.finch._
import io.finch.items._
import scala.reflect.ClassTag

private abstract class Header[F[_], A](
  name: String,
  d: DecodeEntity[A],
  tag: ClassTag[A]
) extends Endpoint[F[A]] with (Try[A] => Try[Output[F[A]]]) { self =>

  protected def missing(name: String): Future[Output[F[A]]]
  protected def present(value: A): F[A]

  final def apply(ta: Try[A]): Try[Output[F[A]]] = ta match {
    case Return(a) => Return(Output.payload(present(a)))
    case Throw(e) => Throw(Error.NotParsed(items.HeaderItem(name), tag, e))
  }

  final def apply(input: Input): Endpoint.Result[F[A]] = {
    val output = Rerunnable.fromFuture {
      input.request.headerMap.getOrNull(name) match {
        case null => missing(name)
        case value => Future.const(d(value).transform(self))
      }
    }

    EndpointResult.Matched(input, Trace.empty, output)
  }

  final override def item: RequestItem = items.HeaderItem(name)
  final override def toString: String = s"header($name)"
}

private object Header {

  private val noneInstance: Future[Output[Option[Nothing]]] = Future.value(Output.None)
  private def none[A] = noneInstance.asInstanceOf[Future[Output[Option[A]]]]

  trait Required[A] { _: Header[Id, A] =>
    protected def missing(name: String): Future[Output[A]] =
      Future.exception(Error.NotPresent(items.HeaderItem(name)))
    protected def present(value: A): Id[A] = value
  }

  trait Optional[A] { _: Header[Option, A] =>
    protected def missing(name: String): Future[Output[Option[A]]] = none[A]
    protected def present(value: A): Option[A] = Some(value)
  }
}

private[finch] trait Headers {

  /**
   * An evaluating [[Endpoint]] that reads a required HTTP header `name` from the request or raises
   * an [[Error.NotPresent]] exception when the header is missing.
   */
  def header[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[A] =
    new Header[Id, A](name, d, tag) with Header.Required[A]

  /**
   * An evaluating [[Endpoint]] that reads an optional HTTP header `name` from the request into an
   * `Option`.
   */
  def headerOption[A](name: String)(implicit
    d: DecodeEntity[A],
    tag: ClassTag[A]
  ): Endpoint[Option[A]] = new Header[Option, A](name, d, tag) with Header.Optional[A]
}
