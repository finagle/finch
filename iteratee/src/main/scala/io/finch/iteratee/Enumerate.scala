package io.finch.iteratee

import java.nio.charset.Charset

import com.twitter.io.Buf
import com.twitter.util.Future
import io.finch.Application
import io.iteratee.Enumerator

/**
  * Enumerate HTTP streamed payload represented as [[Enumerator]] (encoded with [[Charset]]) into
  * an [[Enumerator]] of arbitrary type `A`.
  */
trait Enumerate[A] {

  type ContentType <: String

  def apply(enumerator: Enumerator[Future, Buf], cs: Charset): Enumerator[Future, A]
}

object Enumerate extends EnumerateInstances {

  type Aux[A, CT <: String] = Enumerate[A] {type ContentType = CT}

  type Json[A] = Aux[A, Application.Json]
}

trait EnumerateInstances {
  def instance[A, CT <: String]
  (f: (Enumerator[Future, Buf], Charset) => Enumerator[Future, A]): Enumerate.Aux[A, CT] = new Enumerate[A] {
    type ContentType = CT

    def apply(enumerator: Enumerator[Future, Buf], cs: Charset): Enumerator[Future, A] = f(enumerator, cs)
  }

  implicit def buf2bufDecode[CT <: String]: Enumerate.Aux[Buf, CT] =
    instance[Buf, CT]((enum, _) => enum)
}
