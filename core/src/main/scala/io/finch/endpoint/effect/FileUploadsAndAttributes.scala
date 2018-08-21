package io.finch.endpoint.effect

import cats.data.NonEmptyList
import com.twitter.finagle.http.exp.Multipart
import io.finch._
import scala.reflect.ClassTag


trait FileUploadsAndAttributes[F[_]] { self: EffectInstances[F] =>

  /**
    * An evaluating [[Endpoint]] that reads an optional file upload from a `multipart/form-data`
    * request into an `Option`.
    */
  def multipartFileUploadOption(name: String): Endpoint[F, Option[Multipart.FileUpload]] =
    io.finch.endpoint.multipartFileUploadOption[F](name)
  /**
    * An evaluating [[Endpoint]] that reads a required file upload from a `multipart/form-data`
    * request.
    */
  def multipartFileUpload(name: String): Endpoint[F, Multipart.FileUpload] =
    io.finch.endpoint.multipartFileUpload[F](name)


  /**
    * An evaluating [[Endpoint]] that optionally reads multiple file uploads from a
    * `multipart/form-data` request.
    */
  def multipartFileUploads(name: String): Endpoint[F, Seq[Multipart.FileUpload]] =
    io.finch.endpoint.multipartFileUploads[F](name)

  /**
    * An evaluating [[Endpoint]] that requires multiple file uploads from a `multipart/form-data`
    * request.
    */
  def multipartFileUploadsNel(name: String): Endpoint[F, NonEmptyList[Multipart.FileUpload]] =
    io.finch.endpoint.multipartFileUploadsNel[F](name)

  /**
    * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttribute[A](name: String)(implicit
    d: DecodeEntity[A],
    tag: ClassTag[A]
  ): Endpoint[F, A] = io.finch.endpoint.multipartAttribute[F, A](name)

  /**
    * An evaluating [[Endpoint]] that reads an optional attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributeOption[A](name: String)(implicit
    d: DecodeEntity[A],
    tag: ClassTag[A]
  ): Endpoint[F, Option[A]] = io.finch.endpoint.multipartAttributeOption[F, A](name)

  /**
    * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributes[A](name: String)(implicit
    d: DecodeEntity[A],
    tag: ClassTag[A]
  ): Endpoint[F, Seq[A]] = io.finch.endpoint.multipartAttributes[F, A](name)

  /**
    * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributesNel[A](name: String)(implicit
    d: DecodeEntity[A],
    t: ClassTag[A]
  ): Endpoint[F, NonEmptyList[A]] = io.finch.endpoint.multipartAttributesNel[F, A](name)

}
