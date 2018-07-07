package io.finch.endpoint

import cats.Id
import cats.data.NonEmptyList
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.exp.{Multipart => FinagleMultipart, MultipartDecoder}
import com.twitter.finagle.http.exp.Multipart.{FileUpload => FinagleFileUpload}
import com.twitter.util.{Future, Throw}
import io.catbird.util.Rerunnable
import io.finch._
import io.finch.items._
import scala.reflect.ClassTag
import scala.util.control.NonFatal

private abstract class Attribute[F[_], A](val name: String, d: DecodeEntity[A], tag: ClassTag[A])
  extends Endpoint[F[A]] {

  protected def missing(name: String): Future[Output[F[A]]]
  protected def present(value: NonEmptyList[A]): Future[Output[F[A]]]
  protected def unparsed(errors: NonEmptyList[Throwable], tag: ClassTag[A]): Future[Output[F[A]]]

  private def all(input: Input): Option[NonEmptyList[String]] = {
    for {
      m <- Multipart.decodeIfNeeded(input.request)
      attrs <- m.attributes.get(name)
      nel <- NonEmptyList.fromList(attrs.toList)
    } yield nel
  }

  final def apply(input: Input): Endpoint.Result[F[A]] = {
    if (input.request.isChunked) EndpointResult.NotMatched
    else {
      val output = Rerunnable.fromFuture {
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

private object Attribute {

  private val noneInstance: Future[Output[Option[Nothing]]] = Future.value(Output.None)
  private def none[A] = noneInstance.asInstanceOf[Future[Output[Option[A]]]]

  private val nilInstance: Future[Output[Seq[Nothing]]] =  Future.value(Output.payload(Nil))
  private def nil[A] = nilInstance.asInstanceOf[Future[Output[Seq[A]]]]

  trait SingleError[F[_], A] { _: Attribute[F, A] =>
    protected def unparsed(
      errors: NonEmptyList[Throwable],
      tag: ClassTag[A]
    ): Future[Output[F[A]]] =
      Future.exception(Error.NotParsed(items.ParamItem(name), tag, errors.head))

    final override def toString: String = s"attribute($name)"
  }

  trait MultipleErrors[F[_], A] { _: Attribute[F, A] =>
    protected def unparsed(
      errors: NonEmptyList[Throwable],
      tag: ClassTag[A]
    ): Future[Output[F[A]]] =
      Future.exception(Errors(errors.map(t => Error.NotParsed(items.ParamItem(name), tag, t))))

    final override def toString: String = s"attributes($name)"
  }

  trait Required[A] { _: Attribute[Id, A] =>
    protected def missing(name: String): Future[Output[A]] =
      Future.exception(Error.NotPresent(items.ParamItem(name)))
    protected def present(value: NonEmptyList[A]): Future[Output[A]] =
      Future.value(Output.payload(value.head))
  }

  trait Optional[A] { _: Attribute[Option, A] =>
    protected def missing(name: String): Future[Output[Option[A]]] = none[A]
    protected def present(value: NonEmptyList[A]): Future[Output[Option[A]]] =
      Future.value(Output.payload(Some(value.head)))
  }

  trait AllowEmpty[A] { _: Attribute[Seq, A] =>
    protected def missing(name: String): Future[Output[Seq[A]]] = nil[A]
    protected def present(value: NonEmptyList[A]): Future[Output[Seq[A]]] =
      Future.value(Output.payload(value.toList))
  }

  trait NonEmpty[A] { _: Attribute[NonEmptyList, A] =>
    protected def missing(name: String): Future[Output[NonEmptyList[A]]] =
      Future.exception(Error.NotPresent(items.ParamItem(name)))
    protected def present(value: NonEmptyList[A]): Future[Output[NonEmptyList[A]]] =
      Future.value(Output.payload(value))
  }
}

private abstract class FileUpload[F[_]](name: String)
    extends Endpoint[F[FinagleMultipart.FileUpload]] {

  protected def missing(name: String): Future[Output[F[FinagleFileUpload]]]
  protected def present(a: NonEmptyList[FinagleFileUpload]): Future[Output[F[FinagleFileUpload]]]

  private final def all(input: Input): Option[NonEmptyList[FinagleFileUpload]] =
    for {
      mp <- Multipart.decodeIfNeeded(input.request)
      all <- mp.files.get(name)
      nel <- NonEmptyList.fromList(all.toList)
    } yield nel

  final def apply(input: Input): Endpoint.Result[F[FinagleFileUpload]] =
    if (input.request.isChunked) EndpointResult.NotMatched
    else {
      val output = Rerunnable.fromFuture {
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


private object FileUpload {
  private val noneInstance: Future[Output[Option[Nothing]]] = Future.value(Output.None)
  private def none[A] = noneInstance.asInstanceOf[Future[Output[Option[A]]]]

  private val nilInstance: Future[Output[Seq[Nothing]]] =  Future.value(Output.payload(Nil))
  private def nil[A] = nilInstance.asInstanceOf[Future[Output[Seq[A]]]]

  trait Required { _: FileUpload[Id] =>
    protected def missing(name: String): Future[Output[FinagleFileUpload]] =
      Future.exception(Error.NotPresent(items.ParamItem(name)))
    protected def present(a: NonEmptyList[FinagleFileUpload]): Future[Output[FinagleFileUpload]] =
      Future.value(Output.payload(a.head))
  }

  trait Optional { _: FileUpload[Option] =>
    protected def missing(name: String): Future[Output[Option[FinagleFileUpload]]] =
      none[FinagleFileUpload]
    protected def present(
      a: NonEmptyList[FinagleFileUpload]
    ): Future[Output[Option[FinagleFileUpload]]] = Future.value(Output.payload(Some(a.head)))
  }

  trait AllowEmpty { _: FileUpload[Seq] =>
    protected def missing(name: String): Future[Output[Seq[FinagleFileUpload]]] =
      nil[FinagleFileUpload]
    protected def present(
      fa: NonEmptyList[FinagleFileUpload]
    ): Future[Output[Seq[FinagleFileUpload]]] = Future.value(Output.payload(fa.toList))
  }

  trait NonEmpty { _: FileUpload[NonEmptyList] =>
    protected def missing(name: String): Future[Output[NonEmptyList[FinagleFileUpload]]] =
      Future.exception(Error.NotPresent(items.ParamItem(name)))
    protected def present(
      fa: NonEmptyList[FinagleFileUpload]
    ): Future[Output[NonEmptyList[FinagleFileUpload]]] = Future.value(Output.payload(fa))
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
   * An evaluating [[Endpoint]] that optionally reads multiple file uploads from a
   * `multipart/form-data` request.
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
  def multipartAttribute[A](name: String)(implicit
    d: DecodeEntity[A],
    tag: ClassTag[A]
  ): Endpoint[A] =
    new Attribute[Id, A](name, d, tag)
      with Attribute.Required[A]
      with Attribute.SingleError[Id, A]

  /**
    * An evaluating [[Endpoint]] that reads an optional attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributeOption[A](name: String)(implicit
    d: DecodeEntity[A],
    tag: ClassTag[A]
  ): Endpoint[Option[A]] =
    new Attribute[Option, A](name, d, tag)
      with Attribute.Optional[A]
      with Attribute.SingleError[Option, A]

  /**
    * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributes[A](name: String)(implicit
    d: DecodeEntity[A],
    tag: ClassTag[A]
  ): Endpoint[Seq[A]] =
    new Attribute[Seq, A](name, d, tag)
      with Attribute.AllowEmpty[A]
      with Attribute.MultipleErrors[Seq, A]

  /**
    * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributesNel[A](name: String)(implicit
    d: DecodeEntity[A],
    t: ClassTag[A]
  ): Endpoint[NonEmptyList[A]] =
    new Attribute[NonEmptyList, A](name, d, t)
      with Attribute.NonEmpty[A]
      with Attribute.MultipleErrors[NonEmptyList, A]
}
