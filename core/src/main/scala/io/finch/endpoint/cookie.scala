package io.finch.endpoint

import cats.effect.Effect
import com.twitter.finagle.http.{Cookie => FinagleCookie}
import io.finch._

private[finch] abstract class Cookie[F[_], A](name: String)(implicit
  protected val F: Effect[F]
) extends Endpoint[F, A] {

  protected def missing(name: String): F[Output[A]]
  protected def present(value: FinagleCookie): F[Output[A]]

  def apply(input: Input): EndpointResult[F, A] = {
    val output = F.suspend {
      input.request.cookies.get(name) match {
        case None => missing(name)
        case Some(value) => present(value)
      }
    }

    EndpointResult.Matched(input, Trace.empty, output)
  }

  final override def item: items.RequestItem = items.CookieItem(name)
  final override def toString: String = s"cookie($name)"
}

private[finch] object Cookie {

  trait Optional[F[_]] { _: Cookie[F, Option[FinagleCookie]] =>
    protected def missing(name: String): F[Output[Option[FinagleCookie]]] = F.pure(Output.None)
    protected def present(value: FinagleCookie): F[Output[Option[FinagleCookie]]] =
      F.pure(Output.payload(Some(value)))
  }

  trait Required[F[_]] { _: Cookie[F, FinagleCookie] =>
    protected def missing(name: String): F[Output[FinagleCookie]] =
      F.raiseError(Error.NotPresent(items.CookieItem(name)))
    protected def present(value: FinagleCookie): F[Output[FinagleCookie]] =
      F.pure(Output.payload(value))
  }
}
