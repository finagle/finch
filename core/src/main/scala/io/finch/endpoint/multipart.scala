package io.finch.endpoint

import scala.reflect.ClassTag

import cats.Id
import cats.data.NonEmptyList
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.exp.{Multipart => FinagleMultipart, MultipartDecoder}
import com.twitter.finagle.http.exp.Multipart.{FileUpload => FinagleFileUpload}
import com.twitter.util.Throw
import io.catbird.util.Rerunnable
import io.finch._
import io.finch.internal.Rs
import io.finch.items._
import scala.util.control.NonFatal


private abstract class Attribute[F[_], A](val name: String, val d: DecodeEntity[A], val tag: ClassTag[A])
  extends Endpoint[F[A]] {

  protected def missing(name: String): Rerunnable[Output[F[A]]]
  protected def present(value: NonEmptyList[A]): Rerunnable[Output[F[A]]]
  protected def unparsed(errors: NonEmptyList[Throwable]): Rerunnable[Output[F[A]]]

  private def all(input: Input): Option[NonEmptyList[String]] = {
    for {
      m <- Multipart.decodeIfNeeded(input.request)
      attrs <- m.attributes.get(name)
      nel <- NonEmptyList.fromList(attrs.toList)
    } yield {
      nel
    }
  }

  final def apply(input: Input): Endpoint.Result[F[A]] = {
    if (input.request.isChunked) EndpointResult.NotMatched
    else {
      all(input) match {
        case None => EndpointResult.Matched(input, missing(name))
        case Some(values) =>
          val decoded = values.map(d.apply)
          val errors = decoded.collect {
            case Throw(t) => t
          }
          NonEmptyList.fromList(errors) match {
            case None => EndpointResult.Matched(input, present(decoded.map(_.get())))
            case Some(es) => EndpointResult.Matched(input, unparsed(es))
          }
      }
    }
  }

  final override def item: items.RequestItem = items.ParamItem(name)

}

private object Attribute {

  trait SingleError[F[_], A] { _: Attribute[F, A] =>

    protected def unparsed(errors: NonEmptyList[Throwable]): Rerunnable[Output[F[A]]] =
      Rs.paramNotParsed(name, tag, errors.head)

    final override def toString: String = s"attribute($name)"

  }

  trait MultipleErrors[F[_], A] { _: Attribute[F, A] =>

    protected def unparsed(errors: NonEmptyList[Throwable]): Rerunnable[Output[F[A]]] =
      Rs.paramsNotParsed(name, tag, errors)

    final override def toString: String = s"attributes($name)"

  }

  trait Required[A] { _: Attribute[Id, A] =>
    protected def missing(name: String): Rerunnable[Output[A]] = Rs.paramNotPresent(name)
    protected def present(value: NonEmptyList[A]): Rerunnable[Output[A]] = Rs.payload(value.head)
  }

  trait Optional[A] { _: Attribute[Option, A] =>
    protected def missing(name: String): Rerunnable[Output[Option[A]]] = Rs.none
    protected def present(value: NonEmptyList[A]): Rerunnable[Output[Option[A]]] = Rs.payload(Some(value.head))
  }

  trait AllowEmpty[A] { _: Attribute[Seq, A] =>
    protected def missing(name: String): Rerunnable[Output[Seq[A]]] = Rs.nil
    protected def present(value: NonEmptyList[A]): Rerunnable[Output[Seq[A]]] = Rs.payload(value.toList)
  }

  trait NonEmpty[A] { _: Attribute[NonEmptyList, A] =>
    protected def missing(name: String): Rerunnable[Output[NonEmptyList[A]]] = Rs.paramNotPresent(name)
    protected def present(value: NonEmptyList[A]): Rerunnable[Output[NonEmptyList[A]]] = Rs.payload(value)
  }
}

private abstract class FileUpload[F[_]](name: String) extends Endpoint[F[FinagleMultipart.FileUpload]] {

  protected def missing(name: String): Rerunnable[Output[F[FinagleFileUpload]]]
  protected def present(a: NonEmptyList[FinagleFileUpload]): Rerunnable[Output[F[FinagleFileUpload]]]

  private final def all(input: Input): Option[NonEmptyList[FinagleFileUpload]] =
    for {
      mp <- Multipart.decodeIfNeeded(input.request)
      all <- mp.files.get(name)
      nel <- NonEmptyList.fromList(all.toList)
    } yield nel

  final def apply(input: Input): Endpoint.Result[F[FinagleFileUpload]] =
    if (input.request.isChunked) EndpointResult.NotMatched
    else {
      all(input) match {
        case Some(nel) => EndpointResult.Matched(input, present(nel))
        case None => EndpointResult.Matched(input, missing(name))
      }
    }

  final override def item: RequestItem = ParamItem(name)
  final override def toString: String = name
}


private object FileUpload {
  trait Required { _: FileUpload[Id] =>
    protected def missing(name: String): Rerunnable[Output[FinagleFileUpload]] =Rs.paramNotPresent(name)
    protected def present(a: NonEmptyList[FinagleFileUpload]): Rerunnable[Output[FinagleFileUpload]] =
      Rs.payload(a.head)
  }

  trait Optional { _: FileUpload[Option] =>
    protected def missing(name: String): Rerunnable[Output[Option[FinagleFileUpload]]] = Rs.none
    protected def present(a: NonEmptyList[FinagleFileUpload]): Rerunnable[Output[Option[FinagleFileUpload]]] =
      Rs.payload(Some(a.head))
  }

  trait AllowEmpty { _: FileUpload[Seq] =>
    protected def missing(name: String): Rerunnable[Output[Seq[FinagleFileUpload]]] = Rs.nil
    protected def present(fa: NonEmptyList[FinagleFileUpload]): Rerunnable[Output[Seq[FinagleFileUpload]]] =
      Rs.payload(fa.toList)
  }

  trait NonEmpty { _: FileUpload[NonEmptyList] =>
    protected def missing(name: String): Rerunnable[Output[NonEmptyList[FinagleFileUpload]]] = Rs.paramNotPresent(name)
    protected def present(fa: NonEmptyList[FinagleFileUpload]): Rerunnable[Output[NonEmptyList[FinagleFileUpload]]] =
      Rs.payload(fa)
  }
}

private object Multipart {
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

private[finch] trait FileUploadsAndAttributes {

  /**
   * An evaluating [[Endpoint]] that reads an optional file upload from a `multipart/form-data`
   * request into an `Option`.
   */
  def multipartFileUploadOption(name: String): Endpoint[Option[FinagleMultipart.FileUpload]] =
    new FileUpload[Option](name) with FileUpload.Optional

  /**
   * An evaluating [[Endpoint]] that reads a required file upload from a `multipart/form-data`
   * request.
   */
  def multipartFileUpload(name: String): Endpoint[FinagleMultipart.FileUpload] =
    new FileUpload[Id](name) with FileUpload.Required

  /**
   * An evaluating [[Endpoint]] that optionally reads multiple file uploads from a `multipart/form-data`
   * request.
   */
  def multipartFileUploads(name: String): Endpoint[Seq[FinagleMultipart.FileUpload]] =
    new FileUpload[Seq](name) with FileUpload.AllowEmpty

  /**
   * An evaluating [[Endpoint]] that requires multiple file uploads from a `multipart/form-data`
   * request.
   */
  def multipartFileUploadsNel(name: String): Endpoint[NonEmptyList[FinagleMultipart.FileUpload]] =
    new FileUpload[NonEmptyList](name) with FileUpload.NonEmpty

  /**
    * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttribute[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[A] =
    new Attribute[Id, A](name, d, tag) with Attribute.Required[A] with Attribute.SingleError[Id, A]

  /**
    * An evaluating [[Endpoint]] that reads an optional attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributeOption[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[Option[A]] =
    new Attribute[Option, A](name, d, tag) with Attribute.Optional[A] with Attribute.SingleError[Option, A]

  /**
    * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributes[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[Seq[A]] =
    new Attribute[Seq, A](name, d, tag) with Attribute.AllowEmpty[A] with Attribute.MultipleErrors[Seq, A]

  /**
    * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributesNel[A](name: String)(implicit d: DecodeEntity[A], t: ClassTag[A]): Endpoint[NonEmptyList[A]] =
    new Attribute[NonEmptyList, A](name, d, t) with Attribute.NonEmpty[A] with Attribute.MultipleErrors[NonEmptyList, A]
}
