package io.finch

import com.twitter.util.Future
import shapeless.HNil
import shapeless.ops.function.FnToProduct

import io.finch.Endpoint._

/**
 * A type class that allows the [[Endpoint]] to be mapped to either `A => B` or `A => Future[B]`.
 */
trait Mapper[A] {
  type Out

  def apply(r: Endpoint[A]): Endpoint[Out]
}

trait LowPriorityMapperConversions {
  type Aux[A, B] = Mapper[A] { type Out = B }

  @deprecated("Use an explicit output (i.e., Ok()) instead.", "0.9.0")
  implicit def mapperFromFunction[A, B](f: A => B): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.map(f)
  }

  @deprecated("Use an explicit output (i.e., Ok()) instead.", "0.9.0")
  implicit def mapperFromFutureFunction[A, B](f: A => Future[B]): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.fmap(f)
  }

  implicit def mapperFromOutputFunction[A, B](f: A => Output[B]): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.emap(f)
  }

  implicit def mapperFromOutputFutureFunction[A, B](f: A => Output[Future[B]]): Mapper.Aux[A, B] =
    new Mapper[A] {
      type Out = B
      def apply(r: Endpoint[A]): Endpoint[Out] = r.efmap(f)
    }

  implicit def mapperFromFutureOutputFunction[A, B](f: A => Future[Output[B]]): Mapper.Aux[A, B] =
    new Mapper[A] {
      type Out = B
      def apply(r: Endpoint[A]): Endpoint[Out] = r.femap(f)
    }
}

trait MidPriorityMapperConversions extends LowPriorityMapperConversions {
  @deprecated("Use an explicit output (i.e., Ok()) instead.", "0.9.0")
  implicit def mapperFromHFunction[A, B, F](f: F)(implicit
    ftp: FnToProduct.Aux[F, A => B]
  ): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.map(ftp(f))
  }
}

trait HighPriorityMapperConversions extends MidPriorityMapperConversions {
  implicit def mapperFromOutputHFunction[A, B, F, OB](f: F)(implicit
    ftp: FnToProduct.Aux[F, A => OB],
    ev: OB <:< Output[B]
  ): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.emap(value => ev(ftp(f)(value)))
  }

  @deprecated("Use an explicit output (i.e., Ok()) instead.", "0.9.0")
  implicit def mapperFromFutureHFunction[A, B, F, FB](f: F)(implicit
    ftp: FnToProduct.Aux[F, A => FB],
    ev: FB <:< Future[B]
  ): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.fmap(value => ev(ftp(f)(value)))
  }

  @deprecated("Use an explicit output (i.e., Ok()) instead.", "0.9.0")
  implicit def mapperFromValue[A](v: => A): Mapper.Aux[HNil, A] = new Mapper[HNil] {
    type Out = A
    def apply(r: Endpoint[HNil]): Endpoint[Out] = r.map(_ => v)
  }

  @deprecated("Use an explicit output (i.e., Ok()) instead.", "0.9.0")
  implicit def mapperFromFutureValue[A](f: => Future[A]): Mapper.Aux[HNil, A] = new Mapper[HNil] {
    type Out = A
    def apply(r: Endpoint[HNil]): Endpoint[Out] = r.fmap(_ => f)
  }

  implicit def mapperFromOutputValue[A](o: => Output[A]): Mapper.Aux[HNil, A] = new Mapper[HNil] {
    type Out = A
    def apply(r: Endpoint[HNil]): Endpoint[Out] = r.emap(_ => o)
  }

  implicit def mapperFromOutputFutureValue[A](o: => Output[Future[A]]): Mapper.Aux[HNil, A] =
    new Mapper[HNil] {
      type Out = A
      def apply(r: Endpoint[HNil]): Endpoint[Out] = r.efmap(_ => o)
    }

  implicit def mapperFromFutureOutputValue[A](o: => Future[Output[A]]): Mapper.Aux[HNil, A] =
    new Mapper[HNil] {
      type Out = A
      def apply(r: Endpoint[HNil]): Endpoint[Out] = r.femap(_ => o)
    }
}

object Mapper extends HighPriorityMapperConversions {

  implicit def mapperFromOutputFutureHFunction[A, B, F, OFB](f: F)(implicit
     ftp: FnToProduct.Aux[F, A => OFB],
     ev: OFB <:< Output[Future[B]]
  ): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.efmap(value => ev(ftp(f)(value)))
  }

  implicit def mapperFromFutureOutputHFunction[A, B, F, FOB](f: F)(implicit
    ftp: FnToProduct.Aux[F, A => FOB],
    ev: FOB <:< Future[Output[B]]
  ): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.femap(value => ev(ftp(f)(value)))
  }
}
