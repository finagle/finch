package io.finch.iteratee

import java.nio.charset.Charset

import com.twitter.io.Buf
import com.twitter.util.Future
import io.finch.Application
import io.iteratee.Enumerator

/**
  * Decodes an HTTP streamed payload represented as [[Enumerator]] (encoded with [[Charset]]) into
  * an [[Enumerator]] of arbitrary type `A`.
  */
trait AsyncDecode[A] {

  type ContentType <: String

  def apply(enumerator: Enumerator[Future, Buf], cs: Charset): Enumerator[Future, A]
}

object AsyncDecode extends DecodeInstances {

  type Aux[A, CT <: String] = AsyncDecode[A] {type ContentType = CT}

  type Json[A] = Aux[A, Application.Json]
}

trait DecodeInstances {
  def instance[A, CT <: String]
  (f: (Enumerator[Future, Buf], Charset) => Enumerator[Future, A]): AsyncDecode.Aux[A, CT] = new AsyncDecode[A] {
    override type ContentType = CT

    override def apply(enumerator: Enumerator[Future, Buf], cs: Charset): Enumerator[Future, A] = f(enumerator, cs)
  }

  implicit def buf2bufDecode[CT <: String]: AsyncDecode.Aux[Buf, CT] =
    instance[Buf, CT]((enum, _) => enum)
}
