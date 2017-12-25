package io.finch

import cats.syntax.eq._
import io.finch.internal.Accept
import shapeless._

trait NegotiateToResponse[ContentType] {

  type A

  def apply(accept: Seq[Accept]): ToResponse.Aux[A, ContentType]

}

object NegotiateToResponse extends MediumPriorityNegotiation {

  type Aux[CT, α] = NegotiateToResponse[CT] { type A = α }

  implicit def mkCoproduct[A, CTH <: String, CTT <: Coproduct](implicit
    h: ToResponse.Aux[A, CTH],
    t: NegotiateToResponse.Aux[CTT, A],
    w: Witness.Aux[CTH]
  ): NegotiateToResponse.Aux[CTH :+: CTT, A] = instance { accept =>
    Accept(w.value) match {
      case Some(ct) if accept.exists(_ === ct) =>
        ToResponse.instance[A, CTH :+: CTT]((a, cs) => h(a, cs))
      case _ =>
        ToResponse.instance[A, CTH :+: CTT]((a, cs) => t(accept)(a, cs))
    }
  }

}

trait MediumPriorityNegotiation extends LowPriorityNegotiation {

  implicit def mkLast[A, CTH <: String](implicit
    h: ToResponse.Aux[A, CTH]
  ): NegotiateToResponse.Aux[CTH :+: CNil, A] = instance { _ =>
    ToResponse.instance[A, CTH :+: CNil]((a, cs) => h(a, cs))
  }
}

trait LowPriorityNegotiation {

  def instance[CT, α](f: Seq[Accept] => ToResponse.Aux[α, CT]): NegotiateToResponse.Aux[CT, α] = {
    new NegotiateToResponse[CT] {
      type A = α
      def apply(accept: Seq[Accept]): ToResponse.Aux[A, CT] = f(accept)
    }
  }

  implicit def mkSingle[A, CT <: String](implicit
    tr: ToResponse.Aux[A, CT]
  ): NegotiateToResponse.Aux[CT, A] = instance(_ => tr)

}
