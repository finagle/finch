package io.finch

import java.util.UUID

import io.finch.internal.TooFastString

/**
  * Decodes an HTTP path (eg: /foo/bar/baz) represented as UTF-8 `String` into
  * an arbitrary type `A`.
  */
trait DecodePath[A] {
  def apply(s: String): Option[A]
}

object DecodePath {

  @inline def apply[A](implicit d: DecodePath[A]): DecodePath[A] = d

  def instance[A](fn: String => Option[A]): DecodePath[A] = new DecodePath[A] {
    def apply(s: String): Option[A] = fn(s)
  }

  implicit val decodePath: DecodePath[String] = instance(Some.apply)
  implicit val decodeInt: DecodePath[Int] = instance(_.tooInt)
  implicit val decodeLong: DecodePath[Long] = instance(_.tooLong)
  implicit val decodeBoolean: DecodePath[Boolean] = instance(_.tooBoolean)
  implicit val decodeUUID: DecodePath[UUID] = instance { s =>
    if (s.length != 36) None
    else
      try Some(UUID.fromString(s))
      catch { case _: Exception => None }
  }
}
