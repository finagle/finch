package io.finch.endpoint

import arrows.twitter.Task
import cats.Id
import cats.data.NonEmptyList
import com.twitter.util._
import io.finch._
import scala.reflect.ClassTag

private abstract class Param[F[_], A](
  name: String,
  d: DecodeEntity[A],
  tag: ClassTag[A]
) extends Endpoint[F[A]] with (Try[A] => Try[Output[F[A]]]) { self =>

  protected def missing(name: String): Future[Output[F[A]]]
  protected def present(value: A): F[A]

  final def apply(ta: Try[A]): Try[Output[F[A]]] = ta match {
    case Return(r) => Return(Output.payload(present(r)))
    case Throw(e) => Throw(Error.NotParsed(items.ParamItem(name), tag, e))
  }

  final def apply(input: Input): Endpoint.Result[F[A]] = {
    val output = Task.async {
      input.request.params.get(name) match {
        case None => missing(name)
        case Some(value) => Future.const(d(value).transform(self))
      }
    }

    EndpointResult.Matched(input, Trace.empty, output)
  }

  final override def item: items.RequestItem = items.ParamItem(name)
  final override def toString: String = s"param($name)"
}

private object Param {

  private val noneInstance: Future[Output[Option[Nothing]]] = Future.value(Output.None)

  private def none[A]: Future[Output[Option[A]]] =
    noneInstance.asInstanceOf[Future[Output[Option[A]]]]

  trait Required[A] { _: Param[Id, A] =>
    protected def missing(name: String): Future[Output[A]] =
      Future.exception(Error.NotPresent(items.ParamItem(name)))
    protected def present(a: A): Id[A] = a
  }

  trait Optional[A] { _: Param[Option, A] =>
    protected def missing(name: String): Future[Output[Option[A]]] = none[A]
    protected def present(a: A): Option[A] = Some(a)
  }
}

private abstract class Params[F[_], A](name: String, d: DecodeEntity[A], tag: ClassTag[A])
    extends Endpoint[F[A]] {

  protected def missing(name: String): Future[Output[F[A]]]
  protected def present(value: Iterable[A]): F[A]

  final def apply(input: Input): Endpoint.Result[F[A]] = {
    val output = Task.async {
      input.request.params.getAll(name) match {
        case value if value.isEmpty => missing(name)
        case value =>
          val decoded = value.map(d.apply).toList
          val errors = decoded.collect { case Throw(t) => t }

          NonEmptyList.fromList(errors) match {
            case None =>
              Future.value(Output.payload(present(decoded.map(_.get()))))
            case Some(es) =>
              Future.exception(Errors(es.map(t => Error.NotParsed(items.ParamItem(name), tag, t))))
          }
      }
    }

    EndpointResult.Matched(input, Trace.empty, output)
  }

  final override def item: items.RequestItem = items.ParamItem(name)
  final override def toString: String = s"params($name)"
}

private object Params {

  private val nilInstance: Future[Output[Seq[Nothing]]] =  Future.value(Output.payload(Nil))
  private def nil[A] = nilInstance.asInstanceOf[Future[Output[Seq[A]]]]

  trait AllowEmpty[A] { _: Params[Seq, A] =>
    protected def missing(name: String): Future[Output[Seq[A]]] = nil[A]
    protected def present(value: Iterable[A]): Seq[A] = value.toSeq
  }

  trait NonEmpty[A] { _: Params[NonEmptyList, A] =>
    protected def missing(name: String): Future[Output[NonEmptyList[A]]] =
      Future.exception(Error.NotPresent(items.ParamItem(name)))
    protected def present(value: Iterable[A]): NonEmptyList[A] =
      NonEmptyList.fromListUnsafe(value.toList)
  }
}

private[finch] trait ParamAndParams {
  /**
   * An evaluating [[Endpoint]] that reads an optional query-string param `name` from the request
   * into an `Option`.
   */
  def paramOption[A](name: String)(implicit
    d: DecodeEntity[A],
    tag: ClassTag[A]
  ): Endpoint[Option[A]] = new Param[Option, A](name, d, tag) with Param.Optional[A]

  /**
   * An evaluating [[Endpoint]] that reads a required query-string param `name` from the
   * request or raises an [[Error.NotPresent]] exception when the param is missing; an
   * [[Error.NotValid]] exception is the param is empty.
   */
  def param[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[A] =
    new Param[Id, A](name, d, tag) with Param.Required[A]

  /**
   * An evaluating [[Endpoint]] that reads an optional (in a meaning that a resulting
   * `Seq` may be empty) multi-value query-string param `name` from the request into a `Seq`.
   */
  def params[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[Seq[A]] =
    new Params[Seq, A](name, d, tag) with Params.AllowEmpty[A]

  /**
   * An evaluating [[Endpoint]] that reads a required multi-value query-string param `name`
   * from the request into a `NonEmptyList` or raises a [[Error.NotPresent]] exception
   * when the params are missing or empty.
   */
  def paramsNel[A](name: String)(implicit
    d: DecodeEntity[A],
    tag: ClassTag[A]
  ): Endpoint[NonEmptyList[A]] = new Params[NonEmptyList, A](name, d, tag) with Params.NonEmpty[A]
}
