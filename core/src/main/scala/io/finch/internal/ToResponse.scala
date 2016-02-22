package io.finch.internal

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.http.Response
import com.twitter.io.Buf
import io.finch.Encode
import shapeless._

/**
 * Represents a conversion from `A` to [[Response]].
 */
trait ToResponse[A] {
  type ContentType <: String

  def apply(a: A): Response
}

trait LowPriorityToResponseInstances {
  type Aux[A, CT <: String] = ToResponse[A] { type ContentType = CT }

  /**
   * Constructs an instance from a function.
   */
  def instance[A, CT <: String](f: A => Response): Aux[A, CT] = new ToResponse[A] {
    type ContentType = CT
    def apply(a: A): Response = f(a)
  }

  implicit def valueToResponse[A, CT <: String](implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT]
  ): Aux[A, CT] = instance { a =>
    val rep = Response()
    val buf = e(a)

    if (!buf.isEmpty) {
      rep.content = buf
      rep.contentType = w.value
    }

    rep
  }
}

object ToResponse extends LowPriorityToResponseInstances {

  implicit def asyncBufToResponse[CT <: String](implicit
    w: Witness.Aux[CT]
  ): Aux[AsyncStream[Buf], CT] = instance { a =>
    val rep = Response()
    val writable = rep.writer

    a.foreachF(writable.write).ensure(writable.close())
    rep.contentType = w.value

    rep
  }

  implicit def cnilToResponse[CT <: String]: Aux[CNil, CT] =
    instance(_ => sys.error("impossible"))

  implicit def coproductToResponse[H, T <: Coproduct, CT <: String](implicit
    trH: ToResponse.Aux[H, CT],
    trT: ToResponse.Aux[T, CT]
  ): Aux[H :+: T, CT] = instance {
    case Inl(h) => trH(h)
    case Inr(t) => trT(t)
  }
}
