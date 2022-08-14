package io.finch

import cats.data.{NonEmptyChain, NonEmptyList}
import cats.{Eq, Semigroup, Show}

import scala.reflect.ClassTag
import scala.util.control.NoStackTrace

/** A single error from an [[Endpoint]].
  *
  * This indicates that one of the Finch's built-in components failed. This includes, but not limited by:
  *
  *   - reading a required param, body, header, etc.
  *   - parsing a string-based endpoint with `.as[T]` combinator
  */
sealed abstract class Error extends Exception with NoStackTrace

/** Multiple errors from an [[Endpoint]].
  *
  * This type of error indicates that an endpoint is able to accumulate multiple [[Error]]s into a single instance of [[Errors]] that embeds a non-empty list.
  *
  * Error accumulation happens as part of the `.product` (or `adjoin`, `::`) combinator.
  */
final case class Errors(errors: NonEmptyChain[Error]) extends Exception with NoStackTrace {
  override def getMessage: String =
    "One or more errors reading request:" +
      errors.iterator.map(_.getMessage).mkString(System.lineSeparator + "  ", System.lineSeparator + "  ", "")
}

object Errors {
  def apply(errors: NonEmptyList[Error]): Errors =
    Errors(NonEmptyChain.fromNonEmptyList(errors))

  def of(error: Error, errors: Error*): Errors =
    Errors(NonEmptyChain.of(error, errors: _*))

  implicit val eq: Eq[Errors] = Eq.by(_.errors)
  implicit val semigroup: Semigroup[Errors] =
    (xs, ys) => Errors(xs.errors ++ ys.errors)
}

object Error {
  implicit val eq: Eq[Error] = Eq.by(_.getMessage)
  implicit val show: Show[Error] = _.getMessage

  /** A request entity {{what}} was missing. */
  abstract class NotPresent(what: String) extends Error {
    override def getMessage: String = s"Request is missing a $what."
  }

  final case object BodyNotPresent extends NotPresent("body")
  final case class ParamNotPresent(name: String) extends NotPresent(s"param '$name'")
  final case class HeaderNotPresent(name: String) extends NotPresent(s"header '$name'")
  final case class CookieNotPresent(name: String) extends NotPresent(s"cookie '$name''")

  /** A request entity {{what}} can't be parsed into a given {{targetType}}. */
  abstract class NotParsed(what: String, targetType: ClassTag[_]) extends Error {
    override def getMessage: String = {
      // Note: https://issues.scala-lang.org/browse/SI-2034
      val className = targetType.runtimeClass.getName
      val simpleName = className.substring(className.lastIndexOf(".") + 1)
      val cause = if (getCause == null) "unknown cause" else getCause.getMessage
      s"Request $what cannot be converted to $simpleName: $cause."
    }
  }

  final case class BodyNotParsed(targetType: ClassTag[_]) extends NotParsed("body", targetType)
  final case class ParamNotParsed(name: String, targetType: ClassTag[_]) extends NotParsed(s"param '$name'", targetType)
  final case class HeaderNotParsed(name: String, targetType: ClassTag[_]) extends NotParsed(s"header '$name'", targetType)
  final case class CookieNotParsed(name: String, targetType: ClassTag[_]) extends NotParsed(s"cookie '$name'", targetType)
}
