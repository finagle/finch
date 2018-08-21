package io.finch.endpoint

import cats.Id
import cats.data.NonEmptyList
import cats.effect.Effect
import com.twitter.util.{Return, Throw, Try}
import io.finch._
import scala.reflect.ClassTag

private[finch] class ParamAndParams[F[_] : Effect] {

  abstract class Param[G[_], A](
                                         name: String,
                                         d: DecodeEntity[A],
                                         tag: ClassTag[A]
                                       ) extends Endpoint[F, G[A]] with (Try[A] => Try[Output[G[A]]]) { self =>

    protected def missing(name: String): F[Output[G[A]]]
    protected def present(value: A): G[A]

    final def apply(ta: Try[A]): Try[Output[G[A]]] = ta match {
      case Return(r) => Return(Output.payload(present(r)))
      case Throw(e) => Throw(Error.NotParsed(items.ParamItem(name), tag, e))
    }

    final def apply(input: Input): EndpointResult[F, G[A]] = {
      val output: F[Output[G[A]]] = Effect[F].suspend {
        input.request.params.get(name) match {
          case None => missing(name)
          case Some(value) => d(value).transform(self) match {
            case Return(r) => Effect[F].pure(r)
            case Throw(t) => Effect[F].raiseError(t)
          }
        }
      }

      EndpointResult.Matched(input, Trace.empty, output)
    }

    final override def item: items.RequestItem = items.ParamItem(name)
    final override def toString: String = s"param($name)"
  }

  object Param {

    private val noneInstance: F[Output[Option[Nothing]]] = Effect[F].pure(Output.None)

    private def none[A]: F[Output[Option[A]]] =
      noneInstance.asInstanceOf[F[Output[Option[A]]]]

    trait Required[A] { _: Param[Id, A] =>
      protected def missing(name: String): F[Output[A]] =
        Effect[F].raiseError(Error.NotPresent(items.ParamItem(name)))
      protected def present(a: A): Id[A] = a
    }

    trait Optional[A] { _: Param[Option, A] =>
      protected def missing(name: String): F[Output[Option[A]]] = none[A]
      protected def present(a: A): Option[A] = Some(a)
    }
  }

  abstract class Params[G[_], A](name: String, d: DecodeEntity[A], tag: ClassTag[A])
    extends Endpoint[F, G[A]] {

    protected def missing(name: String): F[Output[G[A]]]
    protected def present(value: Iterable[A]): G[A]

    final def apply(input: Input): EndpointResult[F, G[A]] = {
      val output: F[Output[G[A]]] = Effect[F].suspend {
        input.request.params.getAll(name) match {
          case value if value.isEmpty => missing(name)
          case value =>
            val decoded = value.map(d.apply).toList
            val errors = decoded.collect { case Throw(t) => t }

            NonEmptyList.fromList(errors) match {
              case None =>
                Effect[F].pure(Output.payload(present(decoded.map(_.get()))))
              case Some(es) =>
                Effect[F].raiseError(Errors(es.map(t => Error.NotParsed(items.ParamItem(name), tag, t))))
            }
        }
      }

      EndpointResult.Matched(input, Trace.empty, output)
    }

    final override def item: items.RequestItem = items.ParamItem(name)
    final override def toString: String = s"params($name)"
  }

  object Params {

    private val nilInstance: F[Output[Seq[Nothing]]] =  Effect[F].pure(Output.payload(Nil))
    private def nil[A] = nilInstance.asInstanceOf[F[Output[Seq[A]]]]

    trait AllowEmpty[A] { _: Params[Seq, A] =>
      protected def missing(name: String): F[Output[Seq[A]]] = nil[A]
      protected def present(value: Iterable[A]): Seq[A] = value.toSeq
    }

    trait NonEmpty[A] { _: Params[NonEmptyList, A] =>
      protected def missing(name: String): F[Output[NonEmptyList[A]]] =
        Effect[F].raiseError(Error.NotPresent(items.ParamItem(name)))
      protected def present(value: Iterable[A]): NonEmptyList[A] =
        NonEmptyList.fromListUnsafe(value.toList)
    }
  }

}

private[finch] trait ParamAndParamsEndpoints {

  /**
    * An evaluating [[Endpoint]] that reads an optional query-string param `name` from the request
    * into an `Option`.
    */
  def paramOption[F[_] : Effect, A](name: String)(implicit
                                   d: DecodeEntity[A],
                                   tag: ClassTag[A]
  ): Endpoint[F, Option[A]] = {
    val ps = new ParamAndParams[F]
    new ps.Param[Option, A](name, d, tag) with ps.Param.Optional[A]
  }

  /**
    * An evaluating [[Endpoint]] that reads a required query-string param `name` from the
    * request or raises an [[Error.NotPresent]] exception when the param is missing; an
    * [[Error.NotValid]] exception is the param is empty.
    */
  def param[F[_] : Effect, A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[F, A] = {
    val ps = new ParamAndParams[F]
    new ps.Param[Id, A](name, d, tag) with ps.Param.Required[A]
  }

  /**
    * An evaluating [[Endpoint]] that reads an optional (in a meaning that a resulting
    * `Seq` may be empty) multi-value query-string param `name` from the request into a `Seq`.
    */
  def params[F[_] : Effect, A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[F, Seq[A]] = {
    val ps = new ParamAndParams[F]
    new ps.Params[Seq, A](name, d, tag) with ps.Params.AllowEmpty[A]
  }

  /**
    * An evaluating [[Endpoint]] that reads a required multi-value query-string param `name`
    * from the request into a `NonEmptyList` or raises a [[Error.NotPresent]] exception
    * when the params are missing or empty.
    */
  def paramsNel[F[_] : Effect, A](name: String)(implicit
                                 d: DecodeEntity[A],
                                 tag: ClassTag[A]
  ): Endpoint[F, NonEmptyList[A]] = {
    val ps = new ParamAndParams[F]
    new ps.Params[NonEmptyList, A](name, d, tag) with ps.Params.NonEmpty[A]
  }

}
