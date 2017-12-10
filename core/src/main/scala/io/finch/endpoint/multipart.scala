package io.finch.endpoint

import cats.data.NonEmptyList
import com.twitter.finagle.http.exp.{Multipart => FinagleMultipart}
import io.catbird.util.Rerunnable
import io.finch._
import io.finch.internal.Rs
import io.finch.items._
import scala.util.control.NonFatal

private abstract class Multipart[A, B](name: String) extends Endpoint[B] {
  protected def missing(name: String): Rerunnable[Output[B]]
  protected def present(a: NonEmptyList[A]): Rerunnable[Output[B]]
  protected def fetch(mp: FinagleMultipart): Option[Seq[A]]

  private[this] def multipart(input: Input): Option[FinagleMultipart] =
    try input.request.multipart catch { case NonFatal(_) => None }

  private[this] final def all(input: Input): Option[NonEmptyList[A]] =
    for {
      mp <- multipart(input)
      all <- fetch(mp)
      nel <- NonEmptyList.fromList[A](all.toList)
    } yield nel

    final def apply(input: Input): Endpoint.Result[B] =
      if (input.request.isChunked) EndpointResult.Skipped
      else {
        all(input) match {
          case Some(nel) => EndpointResult.Matched(input, present(nel))
          case None => EndpointResult.Matched(input, missing(name))
        }
      }

  final override def item: RequestItem = ParamItem(name)
  final override def toString: String = name
  final def meta: Endpoint.Meta = EndpointMetadata.Multipart(name)
}

private object Multipart {
  trait Required[A] { _: Multipart[A, A] =>
    protected def missing(name: String): Rerunnable[Output[A]] = Rs.paramNotPresent(name)
    protected def present(a: NonEmptyList[A]): Rerunnable[Output[A]] = Rs.payload(a.head)
  }

  trait Optional[A] { _: Multipart[A, Option[A]] =>
    protected def missing(name: String): Rerunnable[Output[Option[A]]] = Rs.none
    protected def present(a: NonEmptyList[A]): Rerunnable[Output[Option[A]]] = Rs.payload(Some(a.head))
  }

  trait AllowEmpty[A] { _: Multipart[A, Seq[A]] =>
    protected def missing(name: String): Rerunnable[Output[Seq[A]]] = Rs.nil
    protected def present(fa: NonEmptyList[A]): Rerunnable[Output[Seq[A]]] = Rs.payload(fa.toList)
  }

  trait NonEmpty[A] { _: Multipart[A, NonEmptyList[A]] =>
    protected def missing(name: String): Rerunnable[Output[NonEmptyList[A]]] = Rs.paramNotPresent(name)
    protected def present(fa: NonEmptyList[A]): Rerunnable[Output[NonEmptyList[A]]] = Rs.payload(fa)
  }
}

private abstract class FileUpload[A](name: String)
    extends Multipart[FinagleMultipart.FileUpload, A](name) {

  protected def fetch(mp: FinagleMultipart): Option[Seq[FinagleMultipart.FileUpload]] =
    mp.files.get(name)
}

private abstract class Attribute[A](name: String) extends Multipart[String, A](name) {
  protected def fetch(mp: FinagleMultipart): Option[Seq[String]] = mp.attributes.get(name)
}

private[finch] trait FileUploadsAndAttributes {

  /**
   * An evaluating [[Endpoint]] that reads an optional file upload from a `multipart/form-data`
   * request into an `Option`.
   */
  def multipartFileUploadOption(name: String): Endpoint[Option[FinagleMultipart.FileUpload]] =
    new FileUpload[Option[FinagleMultipart.FileUpload]](name)
      with Multipart.Optional[FinagleMultipart.FileUpload]

  /**
   * An evaluating [[Endpoint]] that reads a required file upload from a `multipart/form-data`
   * request.
   */
  def multipartFileUpload(name: String): Endpoint[FinagleMultipart.FileUpload] =
    new FileUpload[FinagleMultipart.FileUpload](name)
      with Multipart.Required[FinagleMultipart.FileUpload]

  /**
   * An evaluating [[Endpoint]] that optionally reads multiple file uploads from a `multipart/form-data`
   * request.
   */
  def multipartFileUploads(name: String): Endpoint[Seq[FinagleMultipart.FileUpload]] =
    new FileUpload[Seq[FinagleMultipart.FileUpload]](name)
      with Multipart.AllowEmpty[FinagleMultipart.FileUpload]

  /**
   * An evaluating [[Endpoint]] that requires multiple file uploads from a `multipart/form-data`
   * request.
   */
  def multipartFileUploadsNel(name: String): Endpoint[NonEmptyList[FinagleMultipart.FileUpload]] =
    new FileUpload[NonEmptyList[FinagleMultipart.FileUpload]](name)
      with Multipart.NonEmpty[FinagleMultipart.FileUpload]

  /**
   * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
   * request.
   */
  def multipartAttribute(name: String): Endpoint[String] =
    new Attribute[String](name) with Multipart.Required[String]

  /**
   * An evaluating [[Endpoint]] that reads an optional attribute from a `multipart/form-data`
   * request.
   */
  def multipartAttributeOption(name: String): Endpoint[Option[String]] =
    new Attribute[Option[String]](name) with Multipart.Optional[String]

  /**
   * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
   * request.
   */
  def multipartAttributes(name: String): Endpoint[Seq[String]] =
    new Attribute[Seq[String]](name) with Multipart.AllowEmpty[String]

  /**
   * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
   * request.
   */
  def multipartAttributesNel(name: String): Endpoint[NonEmptyList[String]] =
    new Attribute[NonEmptyList[String]](name) with Multipart.NonEmpty[String]

  /**
   * An evaluating [[Endpoint]] that reads an optional file upload from a `multipart/form-data`
   * request into an `Option`.
   */
  @deprecated("Use multipartFileUploadOption instead", "0.16")
  def fileUploadOption(name: String): Endpoint[Option[FinagleMultipart.FileUpload]] =
    multipartFileUploadOption(name)

  /**
   * An evaluating [[Endpoint]] that reads a required file upload from a `multipart/form-data`
   * request.
   */
  @deprecated("Use multipartFileUpload instead", "0.16")
  def fileUpload(name: String): Endpoint[FinagleMultipart.FileUpload] = multipartFileUpload(name)
}
