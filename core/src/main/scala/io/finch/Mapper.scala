package io.finch

import com.twitter.util.Future
import shapeless.HNil
import shapeless.ops.function.FnToProduct

/**
 * A type class that allows the [[Endpoint]] to be mapped to either `A => B` or `A => Future[B]`.
 */
trait Mapper[A] {
  type Out

  def apply(r: Endpoint[A]): Endpoint[Out]
}

trait LowPriorityMapperConversions {
  type Aux[A, B] = Mapper[A] { type Out = B }

  implicit def mapperFromFunction[A, B](f: A => B): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.map(f)
  }

  implicit def mapperFromFutureFunction[A, B](f: A => Future[B]): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.embedFlatMap(f)
  }
}

trait MidPriorityMapperConversions extends LowPriorityMapperConversions {
  implicit def mapperFromHFunction[A, B, F](f: F)(implicit
    ftp: FnToProduct.Aux[F, A => B]
  ): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.map(ftp(f))
  }
}

object Mapper extends MidPriorityMapperConversions {
  implicit def mapperFromFutureHFunction[A, B, F, FB](f: F)(implicit
    ftp: FnToProduct.Aux[F, A => FB],
    ev: FB <:< Future[B]
  ): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.embedFlatMap(value => ev(ftp(f)(value)))
  }

  implicit def mapperFromValue[A](v: => A): Mapper.Aux[HNil, A] = new Mapper[HNil] {
    type Out = A
    def apply(r: Endpoint[HNil]): Endpoint[Out] = r.map(_ => v)
  }

  implicit def mapperFromFutureValue[A](f: => Future[A]): Mapper.Aux[HNil, A] = new Mapper[HNil] {
    type Out = A
    def apply(r: Endpoint[HNil]): Endpoint[Out] = r.embedFlatMap(_ => f)
  }
}
