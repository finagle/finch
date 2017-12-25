package io.finch

import io.finch.internal.Accept
import shapeless._

trait ContentTypeNegotiation[ContentType] {

  type A

  def apply(accept: Seq[Accept]): ToResponse.Aux[A, ContentType]

}

object ContentTypeNegotiation extends LowPriorityNegotiation {

  type Aux[CT, α] = ContentTypeNegotiation[CT] { type A = α }

  implicit def mkCoproduct[A, CTH <: String, CTT <: Coproduct](implicit
    h: ToResponse.Aux[A, CTH],
    t: ContentTypeNegotiation.Aux[CTT, A],
    w: Witness.Aux[CTH]
  ): ContentTypeNegotiation.Aux[CTH :+: CTT, A] = instance { accept =>
    Accept(w.value) match {
      case Some(ct) if accept.exists(_.matches(ct)) =>
        ToResponse.instance[A, CTH :+: CTT]((a, cs) => h(a, cs))
      case _ =>
        ToResponse.instance[A, CTH :+: CTT]((a, cs) => t(accept)(a, cs))
    }
  }

}

trait LowPriorityNegotiation {

  def instance[CT, α](f: Seq[Accept] => ToResponse.Aux[α, CT]): ContentTypeNegotiation.Aux[CT, α] = {
    new ContentTypeNegotiation[CT] {
      type A = α
      def apply(accept: Seq[Accept]): ToResponse.Aux[A, CT] = f(accept)
    }
  }

  implicit def mkLast[A, CTH <: String](implicit
    h: ToResponse.Aux[A, CTH]
  ): ContentTypeNegotiation.Aux[CTH :+: CNil, A] = instance { _ =>
    ToResponse.instance[A, CTH :+: CNil]((a, cs) => h(a, cs))
  }

}
