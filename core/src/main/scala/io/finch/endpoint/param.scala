package io.finch.endpoint

import cats.Id
import cats.data.NonEmptyList
import cats.effect.Sync
import cats.syntax.all._
import io.finch._

import scala.reflect.ClassTag

abstract private[finch] class Param[F[_], G[_], A](name: String)(implicit
    d: DecodeEntity[A],
    tag: ClassTag[A],
    protected val F: Sync[F]
) extends Endpoint[F, G[A]] { self =>

  protected def missing(name: String): F[Output[G[A]]]
  protected def present(value: A): G[A]

  final def apply(input: Input): EndpointResult[F, G[A]] = {
    val output: F[Output[G[A]]] = F.defer {
      input.request.params.get(name) match {
        case None        => missing(name)
        case Some(value) =>
          d(value) match {
            case Right(s) => F.pure(Output.payload(present(s)))
            case Left(e)  => F.raiseError(Error.ParamNotParsed(name, tag).initCause(e))
          }
      }
    }

    EndpointResult.Matched(input, Trace.empty, output)
  }

  final override def toString: String = s"param($name)"
}

private[finch] object Param {

  trait Required[F[_], A] { _: Param[F, Id, A] =>
    protected def missing(name: String): F[Output[A]] =
      F.raiseError(Error.ParamNotPresent(name))
    protected def present(a: A): Id[A] = a
  }

  trait Optional[F[_], A] { _: Param[F, Option, A] =>
    protected def missing(name: String): F[Output[Option[A]]] = F.pure(Output.None)
    protected def present(a: A): Option[A] = Some(a)
  }
}

abstract private[finch] class Params[F[_], G[_], A](name: String)(implicit
    d: DecodeEntity[A],
    tag: ClassTag[A],
    protected val F: Sync[F]
) extends Endpoint[F, G[A]] {

  protected def missing(name: String): F[Output[G[A]]]
  protected def present(value: Iterable[A]): G[A]

  final def apply(input: Input): EndpointResult[F, G[A]] = {
    val output: F[Output[G[A]]] = F.defer {
      input.request.params.getAll(name) match {
        case value if value.isEmpty => missing(name)
        case value                  =>
          val (errors, decoded) = value.toList.map(d.apply).separate
          NonEmptyList.fromList(errors) match {
            case None     => F.pure(Output.payload(present(decoded)))
            case Some(es) => F.raiseError(Errors(es.map(Error.ParamNotParsed(name, tag).initCause(_).asInstanceOf[Error])))
          }
      }
    }

    EndpointResult.Matched(input, Trace.empty, output)
  }

  final override def toString: String = s"params($name)"
}

private[finch] object Params {

  trait AllowEmpty[F[_], A] { _: Params[F, List, A] =>
    protected def missing(name: String): F[Output[List[A]]] = F.pure(Output.payload(Nil))
    protected def present(value: Iterable[A]): List[A] = value.toList
  }

  trait NonEmpty[F[_], A] { _: Params[F, NonEmptyList, A] =>
    protected def missing(name: String): F[Output[NonEmptyList[A]]] =
      F.raiseError(Error.ParamNotPresent(name))
    protected def present(value: Iterable[A]): NonEmptyList[A] =
      NonEmptyList.fromListUnsafe(value.toList)
  }
}
