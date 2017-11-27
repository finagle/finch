package io.finch.internal

import io.finch.DecodeEntity
import scala.reflect.ClassTag

/**
 * A temporary type-class supporting an API migration from
 *
 * ```
 * param("foo").as[Int]
 * ```
 *
 * to
 *
 * ```
 * param[Int]("foo")
 * ```
 *
 * , while also resolving a very basic `param("foo")` case as expected.
 *
 * @note This class will be removed after the 0.16 release.
 */
trait ResolveDecodeEntity[-A] {
  type Out

  def classTag: ClassTag[Out]
  def decodeEntity: DecodeEntity[Out]
}

object ResolveDecodeEntity {
  type Aux[A, B] = ResolveDecodeEntity[A] { type Out = B }

  implicit val nothing: Aux[Nothing, String] =
    new ResolveDecodeEntity[Nothing] {
      type Out = String
      def classTag: ClassTag[String] = implicitly[ClassTag[String]]
      def decodeEntity: DecodeEntity[String] = DecodeEntity.decodeString
    }

  implicit def something[A](implicit de: DecodeEntity[A], ct: ClassTag[A]): Aux[A, A] =
    new ResolveDecodeEntity[A] {
      type Out = A
      def classTag: ClassTag[A] = ct
      def decodeEntity: DecodeEntity[A] = de
    }
}
