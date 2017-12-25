package io.finch

import cats.syntax.eq._
import io.finch.internal.Accept
import shapeless._

/**
  * `NegotiateToResponse` enables content negotiation with client.
  */
trait NegotiateToResponse[A, ContentType] {

  /**
    * Pick corresponding instance of `ToResponse` according to `Accept` header of request
    */
  def apply(accept: Seq[Accept]): ToResponse.Aux[A, ContentType]

}

object NegotiateToResponse extends MediumPriorityNegotiation {

  implicit def mkCoproduct[A, CTH <: String, CTT <: Coproduct](implicit
    h: ToResponse.Aux[A, CTH],
    t: NegotiateToResponse[A, CTT],
    w: Witness.Aux[CTH]
  ): NegotiateToResponse[A, CTH :+: CTT] = instance { accept =>
    Accept(w.value) match {
      case Some(ct) if accept.exists(_ === ct) =>
        ToResponse.instance[A, CTH :+: CTT]((a, cs) => h(a, cs))
      case _ =>
        ToResponse.instance[A, CTH :+: CTT]((a, cs) => t(accept)(a, cs))
    }
  }

}

trait MediumPriorityNegotiation {

  def instance[A, CT](f: Seq[Accept] => ToResponse.Aux[A, CT]): NegotiateToResponse[A, CT] = {
    new NegotiateToResponse[A, CT] {
      def apply(accept: Seq[Accept]): ToResponse.Aux[A, CT] = f(accept)
    }
  }

  implicit def mkLast[A, CTH <: String](implicit
    tr: ToResponse.Aux[A, CTH]
  ): NegotiateToResponse[A, CTH :+: CNil] = instance { _ =>
    ToResponse.instance[A, CTH :+: CNil]((a, cs) => tr(a, cs))
  }

  implicit def mkSingle[A, CT <: String](implicit
    tr: ToResponse.Aux[A, CT]
  ): NegotiateToResponse[A, CT] = instance(_ => tr)
}

