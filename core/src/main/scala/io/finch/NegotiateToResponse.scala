package io.finch

import shapeless._

/**
  * `NegotiateToResponse` enables content negotiation with client.
  */
trait NegotiateToResponse[A, ContentType] {

  /**
    * Pick corresponding instance of `ToResponse` according to `Accept` header of a request
    */
  def apply(accept: Seq[Accept]): ToResponse.Aux[A, ContentType]

}

object NegotiateToResponse {

  def instance[A, CT](f: Seq[Accept] => ToResponse.Aux[A, CT]): NegotiateToResponse[A, CT] =
    new NegotiateToResponse[A, CT] {
      def apply(accept: Seq[Accept]): ToResponse.Aux[A, CT] = f(accept)
    }

  implicit def mkCoproduct[A, CTH <: String, CTT <: Coproduct](implicit
    h: ToResponse.Aux[A, CTH],
    t: NegotiateToResponse[A, CTT],
    a: Accept.Matcher[CTH]
  ): NegotiateToResponse[A, CTH :+: CTT] = instance { accept =>
    if (accept.exists(_.matches[CTH])) h.asInstanceOf[ToResponse.Aux[A, CTH :+: CTT]]
    else t(accept).asInstanceOf[ToResponse.Aux[A, CTH :+: CTT]]
  }

  implicit def mkLast[A, CTH <: String](implicit
    tr: ToResponse.Aux[A, CTH]
  ): NegotiateToResponse[A, CTH :+: CNil] = instance { _ =>
    tr.asInstanceOf[ToResponse.Aux[A, CTH :+: CNil]]
  }

  implicit def mkSingle[A, CT <: String](implicit
    tr: ToResponse.Aux[A, CT]
  ): NegotiateToResponse[A, CT] = instance(_ => tr)
}
