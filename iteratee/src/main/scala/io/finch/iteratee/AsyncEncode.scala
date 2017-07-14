package io.finch.iteratee

import java.nio.charset.Charset

import com.twitter.io.Buf
import com.twitter.util.Future
import io.finch.Application
import io.iteratee.Enumerator

trait AsyncEncode[A] {

  type ContentType <: String

  def apply(enumerator: Enumerator[Future, A], cs: Charset): Enumerator[Future, Buf]
}

object AsyncEncode extends EncodeInstances {

  type Aux[A, CT <: String] = AsyncEncode[A] { type ContentType = CT }

  type Json[A] = Aux[A, Application.Json]
}

trait EncodeInstances {

  def instance[A, CT <: String]
      (f: (Enumerator[Future, A], Charset) => Enumerator[Future, Buf]): AsyncEncode.Aux[A, CT] = {
    new AsyncEncode[A] {

      override type ContentType = CT

      override def apply(enumerator: Enumerator[Future, A], cs: Charset): Enumerator[Future, Buf] = f(enumerator, cs)
    }
  }

  implicit def buf2bufEncode[CT <: String]: AsyncEncode.Aux[Buf, CT] = instance((enum, _) => enum)
}
