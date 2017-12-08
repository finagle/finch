package io.finch.endpoint

import scala.reflect.ClassTag

import cats.Id
import cats.data.NonEmptyList
import com.twitter.finagle.http.exp.{Multipart => FinagleMultipart}
import com.twitter.util._
import io.catbird.util.Rerunnable
import io.finch._
import io.finch.internal.Rs

private abstract class Attribute[F[_], A](val name: String, val d: DecodeEntity[A], val tag: ClassTag[A])
  extends Endpoint[F[A]] {

  protected def missing(name: String): Rerunnable[Output[F[A]]]
  protected def present(value: NonEmptyList[A]): Rerunnable[Output[F[A]]]
  protected def notparsed(errors: NonEmptyList[Throwable]): Rerunnable[Output[F[A]]]

  private def multipart(input: Input): Option[FinagleMultipart] =
    Try(input.request.multipart).toOption.flatten

  private def all(input: Input): Option[NonEmptyList[String]] = {
    for {
      m <- multipart(input)
      attrs <- m.attributes.get(name)
      nel <- NonEmptyList.fromList(attrs.toList)
    } yield {
      nel
    }
  }

  final def apply(input: Input): Endpoint.Result[F[A]] = {
    all(input) match {
      case None => EndpointResult.Matched(input, missing(name))
      case Some(values) =>
        val decoded = values.map(d.apply)
        val errors = decoded.collect {
          case Throw(t) => t
        }
        NonEmptyList.fromList(errors) match {
          case None => EndpointResult.Matched(input, present(decoded.map(_.get())))
          case Some(es) => EndpointResult.Matched(input, notparsed(es))
        }
    }
  }

  final override def item: items.RequestItem = items.AttributeItem(name)

}

private object Attribute {
  trait Required[A] { _: Attribute[Id, A] =>
    protected def missing(name: String): Rerunnable[Output[A]] = Rs.attributeNotPresent(name)
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
    protected def missing(name: String): Rerunnable[Output[NonEmptyList[A]]] = Rs.attributeNotPresent(name)
    protected def present(value: NonEmptyList[A]): Rerunnable[Output[NonEmptyList[A]]] = Rs.payload(value)
  }
}

private trait SingleItem[F[_], A] { _: Attribute[F, A] =>

  protected def notparsed(errors: NonEmptyList[Throwable]): Rerunnable[Output[F[A]]] =
    Rs.attributeNotParsed(name, tag, errors.head)

  final override def toString: String = s"attribute($name)"

}

private trait MultipleItems[F[_], A] { _: Attribute[F, A] =>

  protected def notparsed(errors: NonEmptyList[Throwable]): Rerunnable[Output[F[A]]] =
    Rs.attributesNotParsed(name, tag, errors)

  final override def toString: String = s"attributes($name)"

}

private[finch] trait Attributes {
  /**
    * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttribute[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[A] =
    new Attribute[Id, A](name, d, tag) with Attribute.Required[A] with SingleItem[Id, A]

  /**
    * An evaluating [[Endpoint]] that reads an optional attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributeOption[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[Option[A]] =
    new Attribute[Option, A](name, d, tag) with Attribute.Optional[A] with SingleItem[Option, A]

  /**
    * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributes[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[Seq[A]] =
    new Attribute[Seq, A](name, d, tag) with Attribute.AllowEmpty[A] with MultipleItems[Seq, A]

  /**
    * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
    * request.
    */
  def multipartAttributesNel[A](name: String)(implicit d: DecodeEntity[A], t: ClassTag[A]): Endpoint[NonEmptyList[A]] =
    new Attribute[NonEmptyList, A](name, d, t) with Attribute.NonEmpty[A] with MultipleItems[NonEmptyList, A]

}
