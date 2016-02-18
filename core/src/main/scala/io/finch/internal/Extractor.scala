package io.finch.internal

import java.util.UUID

/**
 * A type class that defines a logic by which a value of type `A` is matched and extracted from the
 * current path segment.
 */
trait Extractor[A] {
  def apply(s: String): Option[A]
}

object Extractor {

  @inline def apply[A](implicit e: Extractor[A]): Extractor[A] = e

  def instance[A](fn: String => Option[A]): Extractor[A] = new Extractor[A] {
    def apply(s: String): Option[A] = fn( s)
  }

  private[this] def extractUUID(s: String): Option[UUID] =
    if (s.length != 36) None
    else try Some(UUID.fromString(s)) catch { case _: Exception => None }

  implicit val identity: Extractor[String] = instance(Some.apply)
  implicit val intMatcher: Extractor[Int] = instance(_.tooInt)
  implicit val longMatcher: Extractor[Long] = instance(_.tooLong)
  implicit val booleanMatcher: Extractor[Boolean] = instance(_.tooBoolean)
  implicit val uuidMatcher: Extractor[UUID] = instance(extractUUID)
}
