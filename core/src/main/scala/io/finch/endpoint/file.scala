package io.finch.endpoint

import cats.Id
import cats.data.NonEmptyList
import com.twitter.finagle.http.exp.{Multipart => FinagleMultipart}
import com.twitter.finagle.http.exp.Multipart.{FileUpload => FinagleFileUpload}
import io.catbird.util.Rerunnable
import io.finch._
import io.finch.internal.Rs
import io.finch.items._
import scala.util.control.NonFatal

private abstract class FileUpload[F[_]](name: String) extends Endpoint[F[FinagleMultipart.FileUpload]] {

  protected def missing(name: String): Rerunnable[Output[F[FinagleFileUpload]]]
  protected def present(a: NonEmptyList[FinagleFileUpload]): Rerunnable[Output[F[FinagleFileUpload]]]

  private def multipart(input: Input): Option[FinagleMultipart] =
    try input.request.multipart catch { case NonFatal(_) => None }

  private final def all(input: Input): Option[NonEmptyList[FinagleFileUpload]] =
    for {
      mp <- multipart(input)
      all <- mp.files.get(name)
      nel <- NonEmptyList.fromList(all.toList)
    } yield nel

  final def apply(input: Input): Endpoint.Result[F[FinagleFileUpload]] =
    if (input.request.isChunked) EndpointResult.Skipped
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

private[finch] trait FileUploads {

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
