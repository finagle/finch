package io.finch.endpoint

import cats.Id
import cats.data.NonEmptyList
import cats.effect.Effect
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.exp.{Multipart => FinagleMultipart, MultipartDecoder}
import com.twitter.finagle.http.exp.Multipart.{FileUpload => FinagleFileUpload}
import com.twitter.util.Throw
import io.finch._
import io.finch.items._
import scala.reflect.ClassTag
import scala.util.control.NonFatal

private[finch] abstract class Attribute[F[_]: Effect, G[_], A](val name: String)(implicit
  d: DecodeEntity[A],
  tag: ClassTag[A]
) extends Endpoint[F, G[A]] {

  protected def F: Effect[F] = Effect[F]
  protected def missing(name: String): F[Output[G[A]]]
  protected def present(value: NonEmptyList[A]): F[Output[G[A]]]
  protected def unparsed(errors: NonEmptyList[Throwable], tag: ClassTag[A]): F[Output[G[A]]]

  private def all(input: Input): Option[NonEmptyList[String]] = {
    for {
      m <- Multipart.decodeIfNeeded(input.request)
      attrs <- m.attributes.get(name)
      nel <- NonEmptyList.fromList(attrs.toList)
    } yield nel
  }

  final def apply(input: Input): EndpointResult[F, G[A]] = {
    if (input.request.isChunked) EndpointResult.NotMatched
    else {
      val output = F.suspend {
        all(input) match {
          case None => missing(name)
          case Some(values) =>
            val decoded = values.map(d.apply)
            val errors = decoded.collect { case Throw(t) => t }

            NonEmptyList.fromList(errors) match {
              case None => present(decoded.map(_.get()))
              case Some(es) => unparsed(es, tag)
            }
        }
      }

      EndpointResult.Matched(input, Trace.empty, output)
    }
  }

  final override def item: items.RequestItem = items.ParamItem(name)
}

private[finch] object Attribute {

  trait SingleError[F[_], G[_], A] { _: Attribute[F, G, A] =>
    protected def unparsed(errors: NonEmptyList[Throwable], tag: ClassTag[A]): F[Output[G[A]]] =
      F.raiseError(Error.NotParsed(items.ParamItem(name), tag, errors.head))

    final override def toString: String = s"attribute($name)"
  }

  trait MultipleErrors[F[_], G[_], A] { _: Attribute[F, G, A] =>
    protected def unparsed(errors: NonEmptyList[Throwable], tag: ClassTag[A]): F[Output[G[A]]] =
      F.raiseError(Errors(errors.map(t => Error.NotParsed(items.ParamItem(name), tag, t))))

    final override def toString: String = s"attributes($name)"
  }

  trait Required[F[_], A] { _: Attribute[F, Id, A] =>
    protected def missing(name: String): F[Output[A]] =
      F.raiseError(Error.NotPresent(items.ParamItem(name)))
    protected def present(value: NonEmptyList[A]): F[Output[A]] =
      F.pure(Output.payload(value.head))
  }

  trait Optional[F[_], A] { _: Attribute[F, Option, A] =>
    protected def missing(name: String): F[Output[Option[A]]] =
      F.pure(Output.None)
    protected def present(value: NonEmptyList[A]): F[Output[Option[A]]] =
      F.pure(Output.payload(Some(value.head)))
  }

  trait AllowEmpty[F[_], A] { _: Attribute[F, Seq, A] =>
    protected def missing(name: String): F[Output[Seq[A]]] =
      F.pure(Output.payload(Nil))
    protected def present(value: NonEmptyList[A]): F[Output[Seq[A]]] =
      F.pure(Output.payload(value.toList))
  }

  trait NonEmpty[F[_], A] { _: Attribute[F, NonEmptyList, A] =>
    protected def missing(name: String): F[Output[NonEmptyList[A]]] =
      F.raiseError(Error.NotPresent(items.ParamItem(name)))
    protected def present(value: NonEmptyList[A]): F[Output[NonEmptyList[A]]] =
      F.pure(Output.payload(value))
  }
}

private[finch] abstract class FileUpload[F[_]: Effect, G[_]](name: String)
  extends Endpoint[F, G[FinagleMultipart.FileUpload]] {

  protected def F: Effect[F] = Effect[F]
  protected def missing(name: String): F[Output[G[FinagleFileUpload]]]
  protected def present(a: NonEmptyList[FinagleFileUpload]): F[Output[G[FinagleFileUpload]]]

  private final def all(input: Input): Option[NonEmptyList[FinagleFileUpload]] =
    for {
      mp <- Multipart.decodeIfNeeded(input.request)
      all <- mp.files.get(name)
      nel <- NonEmptyList.fromList(all.toList)
    } yield nel

  final def apply(input: Input): EndpointResult[F, G[FinagleFileUpload]] =
    if (input.request.isChunked) EndpointResult.NotMatched
    else {
      val output = Effect[F].suspend {
        all(input) match {
          case Some(nel) => present(nel)
          case None => missing(name)
        }
      }

      EndpointResult.Matched(input, Trace.empty, output)
    }

  final override def item: RequestItem = ParamItem(name)
  final override def toString: String = s"fileUpload($name)"
}


private[finch] object FileUpload {

  trait Required[F[_]] { _: FileUpload[F, Id] =>
    protected def missing(name: String): F[Output[FinagleFileUpload]] =
      F.raiseError(Error.NotPresent(items.ParamItem(name)))
    protected def present(a: NonEmptyList[FinagleFileUpload]): F[Output[FinagleFileUpload]] =
      F.pure(Output.payload(a.head))
  }

  trait Optional[F[_]] { _: FileUpload[F, Option] =>
    protected def missing(name: String): F[Output[Option[FinagleFileUpload]]] =
      F.pure(Output.payload(None))
    protected def present(a: NonEmptyList[FinagleFileUpload]): F[Output[Option[FinagleFileUpload]]] =
      F.pure(Output.payload(Some(a.head)))
  }

  trait AllowEmpty[F[_]] { _: FileUpload[F, Seq] =>
    protected def missing(name: String): F[Output[Seq[FinagleFileUpload]]] =
      F.pure(Output.payload(Nil))
    protected def present(fa: NonEmptyList[FinagleFileUpload]): F[Output[Seq[FinagleFileUpload]]] =
      F.pure(Output.payload(fa.toList))
  }

  trait NonEmpty[F[_]] { _: FileUpload[F, NonEmptyList] =>
    protected def missing(name: String): F[Output[NonEmptyList[FinagleFileUpload]]] =
      F.raiseError(Error.NotPresent(items.ParamItem(name)))
    protected def present(fa: NonEmptyList[FinagleFileUpload]): F[Output[NonEmptyList[FinagleFileUpload]]] =
      F.pure(Output.payload(fa))
  }
}

private[finch] object Multipart {
  private val field = Request.Schema.newField[Option[FinagleMultipart]](null)

  def decodeNow(req: Request): Option[FinagleMultipart] =
    try MultipartDecoder.decode(req) catch { case NonFatal(_) => None }

  def decodeIfNeeded(req: Request): Option[FinagleMultipart] = req.ctx(field) match {
    case null => // was never decoded for this request
      val value = decodeNow(req)
      req.ctx.update(field, value)
      value
    case value => value // was already decoded for this request
  }
}
