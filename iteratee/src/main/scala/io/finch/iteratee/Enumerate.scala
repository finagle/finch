package io.finch.iteratee

import java.nio.charset.Charset
import scala.annotation.implicitNotFound

import com.twitter.io.Buf
import io.finch.Application
import io.iteratee.Enumerator

/**
  * Enumerate HTTP streamed payload represented as [[Enumerator]] (encoded with [[Charset]]) into
  * an [[Enumerator]] of arbitrary type `A`.
  */
trait Enumerate[F[_], A] {

  type ContentType <: String

  def apply(enumerator: Enumerator[F, Buf], cs: Charset): Enumerator[F, A]
}

object Enumerate extends EnumerateInstances {

  @implicitNotFound(
"""An Enumerator endpoint requires implicit Enumerate instance in scope, probably decoder for ${A} is missing.

  Make sure ${A} is one of the following:

  * A com.twitter.io.Buf
  * A value of a type with an io.finch.iteratee.Enumerate instance (with the corresponding content-type)
"""
  )
  type Aux[F[_], A, CT <: String] = Enumerate[F, A] {type ContentType = CT}

  type Json[F[_], A] = Aux[F, A, Application.Json]
}

trait EnumerateInstances {
  def instance[F[_], A, CT <: String]
  (f: (Enumerator[F, Buf], Charset) => Enumerator[F, A]): Enumerate.Aux[F, A, CT] = new Enumerate[F, A] {
    type ContentType = CT

    def apply(enumerator: Enumerator[F, Buf], cs: Charset): Enumerator[F, A] = f(enumerator, cs)
  }

  implicit def buf2bufDecode[F[_], CT <: String]: Enumerate.Aux[F, Buf, CT] =
    instance[F, Buf, CT]((enum, _) => enum)
}
