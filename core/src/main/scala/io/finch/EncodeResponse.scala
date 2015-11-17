package io.finch

import com.twitter.io.Buf
import com.twitter.io.Buf.Utf8
import scala.xml.NodeSeq

/**
 * An abstraction that is responsible for encoding the response of type `A`.
 */
trait EncodeResponse[-A] {
  def apply(rep: A): Buf
  def contentType: String
  def charset: Option[String] = Some("utf-8")
}

object EncodeResponse {

  /**
   * Convenience method for creating new [[EncodeResponse]] instances.
   */
  def apply[A](ct: String, cs: Option[String] = Some("utf-8"))(fn: A => Buf): EncodeResponse[A] =
    new EncodeResponse[A] {
      override def apply(rep: A): Buf = fn(rep)
      override def contentType: String = ct
      override def charset: Option[String] = cs
    }

  /**
   * Convenience method for creating new [[EncodeResponse]] instances that treat String contents.
   */
  def fromString[A](ct: String, cs: Option[String] = Some("utf-8"))(fn: A => String): EncodeResponse[A] =
    apply(ct, cs)(fn andThen Utf8.apply)

  /**
   * Converts [[EncodeAnyResponse]] into [[EncodeResponse]].
   */
  implicit def anyToConcreteEncode[A](implicit e: EncodeAnyResponse): EncodeResponse[A] =
    new EncodeResponse[A] {
      def apply(rep: A): Buf = e(rep)
      def contentType: String = e.contentType
    }

  implicit val encodeString: EncodeResponse[String] =
    EncodeResponse.fromString[String]("text/plain")(identity)

  implicit val encodeUnit: EncodeResponse[Unit] =
    EncodeResponse("text/plain")(_ => Buf.Empty)

  implicit val encodeBuf: EncodeResponse[Buf] =
    EncodeResponse("application/octet-stream", None)(identity)

  implicit val encodeException: EncodeResponse[Exception] =
    EncodeResponse.fromString("text/plain")(e => Option(e.getMessage).getOrElse(""))

  implicit val encodeNodeSeq: EncodeResponse[NodeSeq] =
    EncodeResponse.fromString("application/xml")(a => a.toString)
}

/**
 * An abstraction that is responsible for encoding the response of a generic type.
 */
trait EncodeAnyResponse {
  def apply[A](rep: A): Buf
  def contentType: String
}
