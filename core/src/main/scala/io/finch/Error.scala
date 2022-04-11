package io.finch

import cats.data.NonEmptyList
import cats.syntax.eq._
import cats.{Eq, Show}

import scala.compat.Platform.EOL
import scala.reflect.ClassTag
import scala.util.control.NoStackTrace

/** A single error from an [[Endpoint]].
  *
  * This indicates that one of the Finch's built-in components failed. This includes, but not limited by:
  *
  *   - reading a required param, body, header, etc.
  *   - parsing a string-based endpoint with `.as[T]` combinator
  *   - validating an endpoint with `.should` or `shouldNot` combinators
  */
sealed abstract class Error extends Exception with NoStackTrace

/** Multiple errors from an [[Endpoint]].
  *
  * This type of error indicates that an endpoint is able to accumulate multiple [[Error]]s into a single instance of [[Errors]] that embeds a non-empty list.
  *
  * Error accumulation happens as part of the `.product` (or `adjoin`, `::`) combinator.
  */
case class Errors(errors: NonEmptyList[Error]) extends Exception with NoStackTrace {
  override def getMessage: String =
    "One or more errors reading request:" +
      errors.map(_.getMessage).toList.mkString(EOL + "  ", EOL + "  ", "")
}

object Error {

  implicit val eq: Eq[Error] = Eq.instance[Error] { (error1, error2) =>
    error1.getMessage === error2.getMessage
  }

  implicit val valueShow: Show[Error] = Show.show[Error] { error =>
    error.getMessage
  }

  abstract class NotPresent(what: String) extends Error {
    override def getMessage: String = s"Request is missing a $what."
  }
  final case object BodyNotPresent extends NotPresent("body")
  final case class ParamNotPresent(name: String) extends NotPresent(s"param '$name'")
  final case class HeaderNotPresent(name: String) extends NotPresent(s"header '$name'")
  final case class CookieNotPresent(name: String) extends NotPresent(s"cookie '$name''")

  abstract class NotValid(what: String, why: String) extends Error {
    override def getMessage: String = s"Validation failed on request $what: $why"
  }
  final case class BodyNotValid(why: String) extends NotValid("body", why)
  final case class ParamNotValid(name: String, why: String) extends NotValid(s"param '$name'", why)
  final case class HeaderNotValid(name: String, why: String) extends NotValid(s"header '$name'", why)
  final case class CookieNotValid(name: String, why: String) extends NotValid(s"cookie '$name'", why)

  abstract class NotParsed(what: String, targetType: ClassTag[_]) extends Error {
    override def getMessage: String = {
      // Note: https://issues.scala-lang.org/browse/SI-2034
      val className = targetType.runtimeClass.getName
      val simpleName = className.substring(className.lastIndexOf(".") + 1)
      val cause = if (getCause == null) "unknown cause" else getCause.getMessage

      s"Request $what cannot be converted to ${simpleName}: $cause."
    }
  }
  final case class BodyNotParsed(targetType: ClassTag[_]) extends NotParsed("body", targetType)
  final case class ParamNotParsed(name: String, targetType: ClassTag[_]) extends NotParsed(s"param '$name'", targetType)
  final case class HeaderNotParsed(name: String, targetType: ClassTag[_]) extends NotParsed(s"header '$name'", targetType)
  final case class CookieNotParsed(name: String, targetType: ClassTag[_]) extends NotParsed(s"cookie '$name'", targetType)
}
