package io.finch

import java.io.{File, InputStream}

import scala.reflect.ClassTag

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.{ContextShift, Resource, Sync}
import com.twitter.finagle.http.exp.Multipart
import com.twitter.finagle.http.{Cookie, Request}
import com.twitter.io.Buf
import shapeless.HNil

/**
  * Enables users to construct [[Endpoint]] instances without specifying the effect type `F[_]` every
  * time.
  *
  * For example, via extending the `Endpoint.Module[F[_]]`:
  *
  * {{{
  *   import io.finch._
  *   import io.cats.effect.IO
  *
  *   object Main extends App with Endpoint.Module[IO] {
  *     def foo = path("foo")
  *   }
  * }}}
  *
  * It's also possible to instantiate an [[EndpointModule]] for a given effect and import its symbols
  * into the score. For example:
  *
  * {{{
  *   import io.finch._
  *   import io.cats.effect.IO
  *
  *   object Main extends App {
  *     val io = Endpoint[IO]
  *     import io._
  *
  *     def foo = path("foo")
  *   }
  * }}}
  *
  * There is a pre-defined [[EndpointModule]] for Cats' `IO`, available via the import:
  *
  * {{{
  *   import io.finch._
  *   import io.finch.catsEffect._
  *
  *   object Main extends App {
  *     def foo = path("foo")
  *   }
  * }}}
  */
trait EndpointModule[F[_]] {

  /**
    * An alias for [[Endpoint.empty]].
    */
  def empty[A]: Endpoint[F, A] =
    Endpoint.empty[F, A]

  /**
    * An alias for [[Endpoint.zero]].
    */
  def zero(implicit F: Applicative[F]): Endpoint[F, HNil] =
    Endpoint.zero[F]

  /**
    * An alias for [[Endpoint.const]].
    */
  def const[A](a: A)(implicit F: Applicative[F]): Endpoint[F, A] =
    Endpoint.const[F, A](a)

  /**
    * An alias for [[Endpoint.lift()]].
    */
  def lift[A](a: => A)(implicit F: Sync[F]): Endpoint[F, A] =
    Endpoint.lift[F, A](a)

  /**
    * An alias for [[Endpoint.liftAsync]].
    */
  def liftAsync[A](fa: => F[A])(implicit F: Sync[F]): Endpoint[F, A] =
    Endpoint.liftAsync[F, A](fa)

  /**
    * An alias for [[Endpoint.liftOutput]].
    */
  def liftOutput[A](oa: => Output[A])(implicit F: Sync[F]): Endpoint[F, A] =
    Endpoint.liftOutput[F, A](oa)

  /**
    * An alias for [[Endpoint.liftOutputAsync]].
    */
  def liftOutputAsync[A](foa: => F[Output[A]])(implicit F: Sync[F]): Endpoint[F, A] =
    Endpoint.liftOutputAsync[F, A](foa)

  /**
    * An alias for [[Endpoint.fromInputStream]].
    */
  def fromInputStream(stream: Resource[F, InputStream])(implicit
      F: Sync[F],
      S: ContextShift[F]
  ): Endpoint[F, Buf] =
    Endpoint.fromInputStream[F](stream)

  /**
    * An alias for [[Endpoint.fromFile]].
    */
  def fromFile(file: File)(implicit
      F: Sync[F],
      S: ContextShift[F]
  ): Endpoint[F, Buf] =
    Endpoint.fromFile[F](file)

  /**
    * An alias for [[Endpoint.classpathAsset]].
    */
  def classpathAsset(path: String)(implicit F: Sync[F], S: ContextShift[F]): Endpoint[F, Buf] =
    Endpoint.classpathAsset[F](path)

  /**
    * An alias for [[Endpoint.classpathAsset]].
    */
  def filesystemAsset(path: String)(implicit F: Sync[F], S: ContextShift[F]): Endpoint[F, Buf] =
    Endpoint.filesystemAsset[F](path)

  /**
    * An alias for [[Endpoint.root]].
    */
  def root(implicit F: Sync[F]): Endpoint[F, Request] =
    Endpoint.root[F]

  /**
    * An alias for [[Endpoint.pathAny]].
    */
  def pathAny(implicit F: Applicative[F]): Endpoint[F, HNil] =
    Endpoint.pathAny[F]

  /**
    * An alias for [[Endpoint.pathEmpty]].
    */
  def pathEmpty(implicit F: Applicative[F]): Endpoint[F, HNil] =
    Endpoint.pathEmpty[F]

  /**
    * An alias for [[Endpoint.path]].
    */
  def path[A: DecodePath: ClassTag](implicit F: Sync[F]): Endpoint[F, A] =
    Endpoint.path[F, A]

  /**
    * An alias for [[Endpoint.paths]].
    */
  def paths[A: DecodePath: ClassTag](implicit F: Sync[F]): Endpoint[F, List[A]] =
    Endpoint.paths[F, A]

  /**
    * An alias for [[Endpoint.path]].
    *
    * @note This method is implicit such that an implicit conversion `String => Endpoint[F, HNil]`
    *       works.
    */
  implicit def path(s: String)(implicit F: Sync[F]): Endpoint[F, HNil] =
    Endpoint.path[F](s)

  /**
    * An alias for [[Endpoint.get]].
    */
  def get[A](e: Endpoint[F, A]): Endpoint.Mappable[F, A] =
    Endpoint.get[F, A](e)

  /**
    * An alias for [[Endpoint.post]].
    */
  def post[A](e: Endpoint[F, A]): Endpoint.Mappable[F, A] =
    Endpoint.post[F, A](e)

  /**
    * An alias for [[Endpoint.patch]].
    */
  def patch[A](e: Endpoint[F, A]): Endpoint.Mappable[F, A] =
    Endpoint.patch[F, A](e)

  /**
    * An alias for [[Endpoint.delete]].
    */
  def delete[A](e: Endpoint[F, A]): Endpoint.Mappable[F, A] =
    Endpoint.delete[F, A](e)

  /**
    * An alias for [[Endpoint.head]].
    */
  def head[A](e: Endpoint[F, A]): Endpoint.Mappable[F, A] =
    Endpoint.head[F, A](e)

  /**
    * An alias for [[Endpoint.options]].
    */
  def options[A](e: Endpoint[F, A]): Endpoint.Mappable[F, A] =
    Endpoint.options[F, A](e)

  /**
    * An alias for [[Endpoint.put]].
    */
  def put[A](e: Endpoint[F, A]): Endpoint.Mappable[F, A] =
    Endpoint.put[F, A](e)

  /**
    * An alias for [[Endpoint.trace]].
    */
  def trace[A](e: Endpoint[F, A]): Endpoint.Mappable[F, A] =
    Endpoint.trace[F, A](e)

  /**
    * An alias for [[Endpoint.header]].
    */
  def header[A: DecodeEntity: ClassTag](name: String)(implicit F: Sync[F]): Endpoint[F, A] =
    Endpoint.header[F, A](name)

  /**
    * An alias for [[Endpoint.headerOption]].
    */
  def headerOption[A: DecodeEntity: ClassTag](name: String)(implicit F: Sync[F]): Endpoint[F, Option[A]] =
    Endpoint.headerOption[F, A](name)

  /**
    * An alias for [[Endpoint.binaryBodyOption]].
    */
  def binaryBodyOption(implicit F: Sync[F]): Endpoint[F, Option[Array[Byte]]] =
    Endpoint.binaryBodyOption[F]

  /**
    * An alias for [[Endpoint.binaryBody]].
    */
  def binaryBody(implicit F: Sync[F]): Endpoint[F, Array[Byte]] =
    Endpoint.binaryBody[F]

  /**
    * An alias for [[Endpoint.stringBodyOption]].
    */
  def stringBodyOption(implicit F: Sync[F]): Endpoint[F, Option[String]] =
    Endpoint.stringBodyOption[F]

  /**
    * An alias for [[Endpoint.stringBody]].
    */
  def stringBody(implicit F: Sync[F]): Endpoint[F, String] =
    Endpoint.stringBody[F]

  /**
    * An alias for [[Endpoint.bodyOption]].
    */
  def bodyOption[A: ClassTag, CT](implicit F: Sync[F], D: Decode.Dispatchable[A, CT]): Endpoint[F, Option[A]] =
    Endpoint.bodyOption[F, A, CT]

  /**
    * An alias for [[Endpoint.body]].
    */
  def body[A: ClassTag, CT](implicit D: Decode.Dispatchable[A, CT], F: Sync[F]): Endpoint[F, A] =
    Endpoint.body[F, A, CT]

  /**
    * An alias for [[Endpoint.jsonBody]].
    */
  def jsonBody[A: Decode.Json: ClassTag](implicit F: Sync[F]): Endpoint[F, A] =
    Endpoint.jsonBody[F, A]

  /**
    * An alias for [[Endpoint.jsonBodyOption]].
    */
  def jsonBodyOption[A: Decode.Json: ClassTag](implicit F: Sync[F]): Endpoint[F, Option[A]] =
    Endpoint.jsonBodyOption[F, A]

  /**
    * An alias for [[Endpoint.textBody]].
    */
  def textBody[A: Decode.Text: ClassTag](implicit F: Sync[F]): Endpoint[F, A] =
    Endpoint.textBody[F, A]

  /**
    * An alias for [[Endpoint.textBodyOption]].
    */
  def textBodyOption[A: Decode.Text: ClassTag](implicit F: Sync[F]): Endpoint[F, Option[A]] =
    Endpoint.textBodyOption[F, A]

  /**
    * An alias for [[Endpoint.binaryBodyStream]].
    */
  def binaryBodyStream[S[_[_], _]](implicit
      F: Sync[F],
      LR: LiftReader[S, F]
  ): Endpoint[F, S[F, Array[Byte]]] = Endpoint.binaryBodyStream[F, S]

  /**
    * An alias for [[Endpoint.stringBodyStream]].
    */
  def stringBodyStream[S[_[_], _]](implicit
      F: Sync[F],
      LR: LiftReader[S, F]
  ): Endpoint[F, S[F, String]] = Endpoint.stringBodyStream[F, S]

  /**
    * An alias for [[Endpoint.bodyStream]].
    */
  def bodyStream[S[_[_], _], A, CT <: String](implicit
      F: Sync[F],
      LR: LiftReader[S, F],
      A: DecodeStream.Aux[S, F, A, CT]
  ): Endpoint[F, S[F, A]] = Endpoint.bodyStream[F, S, A, CT]

  /**
    * An alias for [[Endpoint.jsonBodyStream]].
    */
  def jsonBodyStream[S[_[_], _], A](implicit
      F: Sync[F],
      LR: LiftReader[S, F],
      A: DecodeStream.Aux[S, F, A, Application.Json]
  ): Endpoint[F, S[F, A]] = Endpoint.jsonBodyStream[F, S, A]

  /**
    * An alias for [[Endpoint.textBodyStream]].
    */
  def textBodyStream[S[_[_], _], A](implicit
      F: Sync[F],
      LR: LiftReader[S, F],
      A: DecodeStream.Aux[S, F, A, Text.Plain]
  ): Endpoint[F, S[F, A]] = Endpoint.textBodyStream[F, S, A]

  /**
    * An alias for [[Endpoint.cookieOption]].
    */
  def cookieOption(name: String)(implicit F: Sync[F]): Endpoint[F, Option[Cookie]] =
    Endpoint.cookieOption[F](name)

  /**
    * An alias for [[Endpoint.cookie]].
    */
  def cookie(name: String)(implicit F: Sync[F]): Endpoint[F, Cookie] =
    Endpoint.cookie[F](name)

  /**
    * An alias for [[Endpoint.paramOption]].
    */
  def paramOption[A: DecodeEntity: ClassTag](name: String)(implicit F: Sync[F]): Endpoint[F, Option[A]] =
    Endpoint.paramOption[F, A](name)

  /**
    * An alias for [[Endpoint.param]].
    */
  def param[A: DecodeEntity: ClassTag](name: String)(implicit F: Sync[F]): Endpoint[F, A] =
    Endpoint.param[F, A](name)

  /**
    * An alias for [[Endpoint.params]].
    */
  def params[A: DecodeEntity: ClassTag](name: String)(implicit F: Sync[F]): Endpoint[F, List[A]] =
    Endpoint.params[F, A](name)

  /**
    * An alias for [[Endpoint.paramsNel]].
    */
  def paramsNel[A: DecodeEntity: ClassTag](name: String)(implicit F: Sync[F]): Endpoint[F, NonEmptyList[A]] =
    Endpoint.paramsNel[F, A](name)

  /**
    * An alias for [[Endpoint.multipartFileUploadOption]].
    */
  def multipartFileUploadOption(name: String)(implicit F: Sync[F]): Endpoint[F, Option[Multipart.FileUpload]] =
    Endpoint.multipartFileUploadOption[F](name)

  /**
    * An alias for [[Endpoint.multipartFileUpload]].
    */
  def multipartFileUpload(name: String)(implicit F: Sync[F]): Endpoint[F, Multipart.FileUpload] =
    Endpoint.multipartFileUpload[F](name)

  /**
    * An alias for [[Endpoint.multipartFileUploads]].
    */
  def multipartFileUploads(name: String)(implicit F: Sync[F]): Endpoint[F, List[Multipart.FileUpload]] =
    Endpoint.multipartFileUploads[F](name)

  /**
    * An alias for [[Endpoint.multipartFileUploadsNel]].
    */
  def multipartFileUploadsNel(name: String)(implicit F: Sync[F]): Endpoint[F, NonEmptyList[Multipart.FileUpload]] =
    Endpoint.multipartFileUploadsNel[F](name)

  /**
    * An alias for [[Endpoint.multipartAttribute]].
    */
  def multipartAttribute[A: DecodeEntity: ClassTag](name: String)(implicit F: Sync[F]): Endpoint[F, A] =
    Endpoint.multipartAttribute[F, A](name)

  /**
    * An alias for [[Endpoint.multipartAttributeOption]].
    */
  def multipartAttributeOption[A: DecodeEntity: ClassTag](name: String)(implicit F: Sync[F]): Endpoint[F, Option[A]] =
    Endpoint.multipartAttributeOption[F, A](name)

  /**
    * An alias for [[Endpoint.multipartAttributes]].
    */
  def multipartAttributes[A: DecodeEntity: ClassTag](name: String)(implicit F: Sync[F]): Endpoint[F, List[A]] =
    Endpoint.multipartAttributes[F, A](name)

  /**
    * An alias for [[Endpoint.multipartAttributesNel]].
    */
  def multipartAttributesNel[A: DecodeEntity: ClassTag](name: String)(implicit F: Sync[F]): Endpoint[F, NonEmptyList[A]] =
    Endpoint.multipartAttributesNel[F, A](name)
}

object EndpointModule {
  def apply[F[_]]: EndpointModule[F] = catsEffect.asInstanceOf[EndpointModule[F]]
}
