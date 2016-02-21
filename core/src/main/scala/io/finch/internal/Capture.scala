package io.finch.internal

import java.util.UUID

/**
 * A type class that defines a logic by which a value of type `A` is matched and captured from the
 * current path segment.
 */
trait Capture[A] {
  def apply(s: String): Option[A]
}

object Capture {

  @inline def apply[A](implicit e: Capture[A]): Capture[A] = e

  def instance[A](fn: String => Option[A]): Capture[A] = new Capture[A] {
    def apply(s: String): Option[A] = fn( s)
  }

  private[this] def extractUUID(s: String): Option[UUID] =
    if (s.length != 36) None
    else try Some(UUID.fromString(s)) catch { case _: Exception => None }

  implicit val identity: Capture[String] = instance(Some.apply)
  implicit val intMatcher: Capture[Int] = instance(_.tooInt)
  implicit val longMatcher: Capture[Long] = instance(_.tooLong)
  implicit val booleanMatcher: Capture[Boolean] = instance(_.tooBoolean)
  implicit val uuidMatcher: Capture[UUID] = instance(extractUUID)
}
