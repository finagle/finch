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

private[finch] class FileUploadsAndAttributes[F[_] : Effect] {

  abstract class Attribute[G[_], A](val name: String, d: DecodeEntity[A], tag: ClassTag[A])
    extends Endpoint[F, G[A]] {

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
        val output = Effect[F].suspend {
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

  object Attribute {

    private val noneInstance: F[Output[Option[Nothing]]] = Effect[F].pure(Output.None)
    private def none[A]: F[Output[Option[A]]] = noneInstance.asInstanceOf[F[Output[Option[A]]]]

    private val nilInstance: F[Output[Seq[Nothing]]] =  Effect[F].pure(Output.payload(Nil))
    private def nil[A]: F[Output[Seq[A]]] = nilInstance.asInstanceOf[F[Output[Seq[A]]]]

    trait SingleError[G[_], A] { _: Attribute[G, A] =>
      protected def unparsed(
                              errors: NonEmptyList[Throwable],
                              tag: ClassTag[A]
                            ): F[Output[G[A]]] =
        Effect[F].raiseError(Error.NotParsed(items.ParamItem(name), tag, errors.head))

      final override def toString: String = s"attribute($name)"
    }

    trait MultipleErrors[G[_], A] { _: Attribute[G, A] =>
      protected def unparsed(
                              errors: NonEmptyList[Throwable],
                              tag: ClassTag[A]
                            ): F[Output[G[A]]] =
        Effect[F].raiseError(Errors(errors.map(t => Error.NotParsed(items.ParamItem(name), tag, t))))

      final override def toString: String = s"attributes($name)"
    }

    trait Required[A] { _: Attribute[Id, A] =>
      protected def missing(name: String): F[Output[A]] =
        Effect[F].raiseError(Error.NotPresent(items.ParamItem(name)))
      protected def present(value: NonEmptyList[A]): F[Output[A]] =
        Effect[F].pure(Output.payload(value.head))
    }

    trait Optional[A] { _: Attribute[Option, A] =>
      protected def missing(name: String): F[Output[Option[A]]] = none[A]
      protected def present(value: NonEmptyList[A]): F[Output[Option[A]]] =
        Effect[F].pure(Output.payload(Some(value.head)))
    }

    trait AllowEmpty[A] { _: Attribute[Seq, A] =>
      protected def missing(name: String): F[Output[Seq[A]]] = nil[A]
      protected def present(value: NonEmptyList[A]): F[Output[Seq[A]]] =
        Effect[F].pure(Output.payload(value.toList))
    }

    trait NonEmpty[A] { _: Attribute[NonEmptyList, A] =>
      protected def missing(name: String): F[Output[NonEmptyList[A]]] =
        Effect[F].raiseError(Error.NotPresent(items.ParamItem(name)))
      protected def present(value: NonEmptyList[A]): F[Output[NonEmptyList[A]]] =
        Effect[F].pure(Output.payload(value))
    }
  }

  abstract class FileUpload[G[_]](name: String)
    extends Endpoint[F, G[FinagleMultipart.FileUpload]] {

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


  object FileUpload {
    private val noneInstance: F[Output[Option[Nothing]]] = Effect[F].pure(Output.None)
    private def none[A]: F[Output[Option[A]]] = noneInstance.asInstanceOf[F[Output[Option[A]]]]

    private val nilInstance: F[Output[Seq[Nothing]]] =  Effect[F].pure(Output.payload(Nil))
    private def nil[A]: F[Output[Seq[A]]] = nilInstance.asInstanceOf[F[Output[Seq[A]]]]

    trait Required { _: FileUpload[Id] =>
      protected def missing(name: String): F[Output[FinagleFileUpload]] =
        Effect[F].raiseError(Error.NotPresent(items.ParamItem(name)))
      protected def present(a: NonEmptyList[FinagleFileUpload]): F[Output[FinagleFileUpload]] =
        Effect[F].pure(Output.payload(a.head))
    }

    trait Optional { _: FileUpload[Option] =>
      protected def missing(name: String): F[Output[Option[FinagleFileUpload]]] =
        none[FinagleFileUpload]
      protected def present(
                             a: NonEmptyList[FinagleFileUpload]
                           ): F[Output[Option[FinagleFileUpload]]] = Effect[F].pure(Output.payload(Some(a.head)))
    }

    trait AllowEmpty { _: FileUpload[Seq] =>
      protected def missing(name: String): F[Output[Seq[FinagleFileUpload]]] =
        nil[FinagleFileUpload]
      protected def present(fa: NonEmptyList[FinagleFileUpload]): F[Output[Seq[FinagleFileUpload]]] =
        Effect[F].pure(Output.payload(fa.toList))
    }

    trait NonEmpty { _: FileUpload[NonEmptyList] =>
      protected def missing(name: String): F[Output[NonEmptyList[FinagleFileUpload]]] =
        Effect[F].raiseError(Error.NotPresent(items.ParamItem(name)))
      protected def present(fa: NonEmptyList[FinagleFileUpload]): F[Output[NonEmptyList[FinagleFileUpload]]] =
        Effect[F].pure(Output.payload(fa))
    }
  }

  object Multipart {
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
}

trait FileUploadsAndAttributesEndpoints[F[_]] {


  /**
    * An evaluating [[Endpoint]] that reads an optional file upload from a `multipart/form-data`
    * request into an `Option`.
    */
  def multipartFileUploadOption(name: String)(implicit
    effect: Effect[F]
  ): Endpoint[F, Option[FinagleMultipart.FileUpload]] = {
    val fa = new FileUploadsAndAttributes[F]
    new fa.FileUpload[Option](name) with fa.FileUpload.Optional
  }

  /**
    * An evaluating [[Endpoint]] that reads a required file upload from a `multipart/form-data`
    * request.
    */
  def multipartFileUpload(name: String)(implicit
    effect: Effect[F]
  ): Endpoint[F, FinagleMultipart.FileUpload] = {
    val fa = new FileUploadsAndAttributes[F]
    new fa.FileUpload[Id](name) with fa.FileUpload.Required
  }

  /**
    * An evaluating [[Endpoint]] that optionally reads multiple file uploads from a
    * `multipart/form-data` request.
    */
  def multipartFileUploads(name: String)(implicit
    effect: Effect[F]
  ): Endpoint[F, Seq[FinagleMultipart.FileUpload]] = {
    val fa = new FileUploadsAndAttributes[F]
    new fa.FileUpload[Seq](name) with fa.FileUpload.AllowEmpty
  }

  /**
    * An evaluating [[Endpoint]] that requires multiple file uploads from a `multipart/form-data`
    * request.
    */
  def multipartFileUploadsNel(name: String)(implicit
    effect: Effect[F]
  ): Endpoint[F, NonEmptyList[FinagleMultipart.FileUpload]] = {
    val fa = new FileUploadsAndAttributes[F]
    new fa.FileUpload[NonEmptyList](name) with fa.FileUpload.NonEmpty
  }

  /**
    * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttribute[A](name: String)(implicit
    effect: Effect[F],
    d: DecodeEntity[A],
    tag: ClassTag[A]
  ): Endpoint[F, A] = {
    val fa = new FileUploadsAndAttributes[F]
    new fa.Attribute[Id, A](name, d, tag)
      with fa.Attribute.Required[A]
      with fa.Attribute.SingleError[Id, A]
  }

  /**
    * An evaluating [[Endpoint]] that reads an optional attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributeOption[A](name: String)(implicit
    effect: Effect[F],
    d: DecodeEntity[A],
    tag: ClassTag[A]
  ): Endpoint[F, Option[A]] = {
    val fa = new FileUploadsAndAttributes[F]
    new fa.Attribute[Option, A](name, d, tag)
      with fa.Attribute.Optional[A]
      with fa.Attribute.SingleError[Option, A]
  }

  /**
    * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributes[A](name: String)(implicit
    effect: Effect[F],
    d: DecodeEntity[A],
    tag: ClassTag[A]
  ): Endpoint[F, Seq[A]] = {
    val fa = new FileUploadsAndAttributes[F]
    new fa.Attribute[Seq, A](name, d, tag)
      with fa.Attribute.AllowEmpty[A]
      with fa.Attribute.MultipleErrors[Seq, A]
  }

  /**
    * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributesNel[A](name: String)(implicit
    effect: Effect[F],
    d: DecodeEntity[A],
    t: ClassTag[A]
  ): Endpoint[F, NonEmptyList[A]] = {
    val fa = new FileUploadsAndAttributes[F]
    new fa.Attribute[NonEmptyList, A](name, d, t)
      with fa.Attribute.NonEmpty[A]
      with fa.Attribute.MultipleErrors[NonEmptyList, A]
  }

}
