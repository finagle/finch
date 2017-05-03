package io.finch.endpoint

import com.twitter.finagle.http.exp.Multipart
import io.catbird.util.Rerunnable
import io.finch._
import io.finch.internal.Rs
import io.finch.items._
import scala.util.control.NonFatal

private abstract class FileUpload[A](name: String) extends Endpoint[A] {
  protected def missing: Rerunnable[Output[A]]
  protected def present(fa: Multipart.FileUpload): Rerunnable[Output[A]]

  private[this] final def multipart(input: Input): Option[Multipart] =
    try input.request.multipart catch { case NonFatal(_) => None }

  private[this] final def firstFile(input: Input): Option[Multipart.FileUpload] =
    for {
      mp <- multipart(input)
      all <- mp.files.get(name)
      first <- all.headOption
    } yield first

    final def apply(input: Input): Endpoint.Result[A] =
      if (input.request.isChunked) EndpointResult.Skipped
      else {
        firstFile(input) match {
          case Some(fa) => EndpointResult.Matched(input, present(fa))
          case None => EndpointResult.Matched(input, missing)
        }
      }

  final override def item: RequestItem = ParamItem(name)
  final override def toString: String = name
}

private[finch] trait FileUploads {

  /**
   * An evaluating [[Endpoint]] that reads an optional file upload from a `multipart/form-data`
   * request into an `Option`.
   */
  def fileUploadOption(name: String): Endpoint[Option[Multipart.FileUpload]] =
    new FileUpload[Option[Multipart.FileUpload]](name) {
      protected def missing: Rerunnable[Output[Option[Multipart.FileUpload]]] =
        Rs.none

      protected def present(
        fa: Multipart.FileUpload
      ): Rerunnable[Output[Option[Multipart.FileUpload]]] = Rs.payload(Some(fa))
    }

  /**
   * An evaluating [[Endpoint]] that reads a required file upload from a multipart/form-data
   * request.
   */
  def fileUpload(name: String): Endpoint[Multipart.FileUpload] =
    new FileUpload[Multipart.FileUpload](name) {
      protected def missing: Rerunnable[Output[Multipart.FileUpload]] =
        Rs.paramNotPresent(name)

      protected def present(
        fa: Multipart.FileUpload
      ): Rerunnable[Output[Multipart.FileUpload]] = Rs.payload(fa)
    }
}
