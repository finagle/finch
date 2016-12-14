package io.finch

import cats.data.NonEmptyList
import io.finch.items.RequestItem
import scala.compat.Platform.EOL
import scala.reflect.ClassTag
import scala.util.control.NoStackTrace

/**
 * A single error from an [[Endpoint]].
 *
 * This indicates that one of the Finch's built-in components failed. This includes, but not
 * limited by:
 *
 * - reading a required param, body, header, etc.
 * - parsing a string-based endpoint with `.as[T]` combinator
 * - validating an endpoint with `.should` or `shouldNot` combinators
 */
sealed abstract class Error extends Exception with NoStackTrace

/**
 * Multiple errors from an [[Endpoint]].
 *
 * This type of error indicates that an endpoint is able to accumulate multiple [[Error]]s
 * into a single instance of [[Errors]] that embeds a non-empty list.
 *
 * Error accumulation happens as part of the `.product` (or `adjoin`, `::`) combinator.
 */
case class Errors(errors: NonEmptyList[Error]) extends Exception with NoStackTrace {
  override def getMessage: String =
    "One or more errors reading request:" +
      errors.map(_.getMessage).toList.mkString(EOL + "  ", EOL + "  ","")
}

object Error {

  /**
   * An exception that indicates a required request item (''header'', ''param'', ''cookie'',
   * ''body'') was missing in the request.
   *
   * @param item the missing request item
   */
  final case class NotPresent(item: RequestItem) extends Error {
    override def getMessage: String = s"Required ${item.description} not present in the request."
  }

  /**
   * An exception that indicates a broken [[ValidationRule]] on the request item.
   *
   * @param item the invalid request item
   * @param rule the rule description
   */
  final case class NotValid(item: RequestItem, rule: String) extends Error {
    override def getMessage: String = s"Validation failed: ${item.description} $rule."
  }

  /**
   * An exception that indicates that a request item could be parsed.
   *
   * @param item the invalid request item
   * @param targetType the type the item should be converted into
   * @param cause the cause of the parsing error
   */
  final case class NotParsed(item: RequestItem, targetType: ClassTag[_], cause: Throwable)
    extends Error {

    override def getMessage: String =
      s"${item.description} cannot be converted to ${targetType.runtimeClass.getSimpleName}: " +
      s"${cause.getMessage}."

    override def getCause: Throwable = cause
  }
}
