package io.finch

import scala.compat.Platform.EOL
import scala.reflect.ClassTag

import cats.data.NonEmptyList
import io.finch.items.RequestItem

/**
 * A basic error from a Finch application.
 */
trait Error extends Exception

object Error {

  def apply(message: String): Error = new Error {
    override def getMessage: String = message
  }

  object RequestErrors {
    @deprecated("Use Multiple instead", "0.11")
    def unapply(e: Throwable): Option[Seq[Throwable]] = Some(Seq(e))
  }

  /**
   * An exception that collects multiple endpoint errors.
   *
   * @param errors the errors collected from various endpoints
   */
  final case class Multiple(errors: NonEmptyList[Throwable]) extends Error {
    override def getMessage: String =
      "One or more errors reading request:" + errors.map(_.getMessage).toList.mkString(EOL + "  ", EOL + "  ","")
  }

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
   * An exception that indicates a broken [[[io.finch.request.ValidationRule ValidationRule]] on the
   * request item.
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
