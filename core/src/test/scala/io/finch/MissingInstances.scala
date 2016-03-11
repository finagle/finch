package io.finch

import java.util.UUID

import algebra.Eq
import com.twitter.util.{Await, Try}

trait MissingInstances {
  implicit def eqTry[A]: Eq[Try[A]] = Eq.fromUniversalEquals
  implicit def eqUUID: Eq[UUID] = Eq.fromUniversalEquals

  def eqEndpoint[A](input: Input): Eq[Endpoint[A]] = new Eq[Endpoint[A]] {

    private def await(result: Endpoint.Result[A]): Option[A] = result.map {
      case (_, eval) => Await.result(eval.value).value
    }

    override def eqv(x: Endpoint[A], y: Endpoint[A]): Boolean = {
      val res1 = await(x(input))
      val res2 = await(y(input))

      res1 == res2
    }
  }
}
