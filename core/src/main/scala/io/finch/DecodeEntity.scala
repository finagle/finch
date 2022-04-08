package io.finch

import shapeless._

import java.util.UUID

/** Decodes an HTTP entity (eg: header, query-string param) represented as UTF-8 `String` into an arbitrary type `A`.
  */
trait DecodeEntity[A] {
  def apply(s: String): Either[Throwable, A]
}

object DecodeEntity extends HighPriorityDecode {

  /** Returns a [[DecodeEntity]] instance for a given type.
    */
  @inline def apply[A](implicit d: DecodeEntity[A]): DecodeEntity[A] = d

  implicit val decodeString: DecodeEntity[String] = instance(s => Right(s))

}

trait HighPriorityDecode extends LowPriorityDecode {

  implicit val decodeInt: DecodeEntity[Int] = instance(s =>
    try Right(s.toInt)
    catch { case e: Throwable => Left(e) }
  )

  implicit val decodeLong: DecodeEntity[Long] = instance(s =>
    try Right(s.toLong)
    catch { case e: Throwable => Left(e) }
  )

  implicit val decodeFloat: DecodeEntity[Float] = instance(s =>
    try Right(s.toFloat)
    catch { case e: Throwable => Left(e) }
  )

  implicit val decodeDouble: DecodeEntity[Double] = instance(s =>
    try Right(s.toDouble)
    catch { case e: Throwable => Left(e) }
  )

  implicit val decodeBoolean: DecodeEntity[Boolean] = instance(s =>
    try Right(s.toBoolean)
    catch { case e: Throwable => Left(e) }
  )

  implicit val decodeUUID: DecodeEntity[UUID] = instance(s =>
    if (s.length != 36) Left(new IllegalArgumentException(s"Too long for UUID: ${s.length}"))
    else
      try Right(UUID.fromString(s))
      catch { case e: Throwable => Left(e) }
  )

}

trait LowPriorityDecode {

  /** Creates an [[DecodeEntity]] instance from a given function `String => Either[Throwable, A]`.
    */
  def instance[A](fn: String => Either[Throwable, A]): DecodeEntity[A] = new DecodeEntity[A] {
    def apply(s: String): Either[Throwable, A] = fn(s)
  }

  /** Creates a [[Decode]] from [[shapeless.Generic]] for single value case classes.
    */
  implicit def decodeFromGeneric[A, H <: HList, E](implicit
      gen: Generic.Aux[A, H],
      ev: (E :: HNil) =:= H,
      de: DecodeEntity[E]
  ): DecodeEntity[A] = instance(s => de(s).right.map(b => gen.from(b :: HNil)))
}
