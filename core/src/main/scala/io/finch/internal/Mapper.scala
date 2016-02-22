package io.finch.internal

import com.twitter.util.Future
import io.finch.{Endpoint, Output}
import shapeless.HNil
import shapeless.ops.function.FnToProduct

/**
 * A type class that allows the [[Endpoint]] to be mapped to either `A => B` or `A => Future[B]`.
 * @groupname LowPriorityMapper Low Priority Mapper Conversions
 * @groupprio LowPriorityMapper 0
 * @groupname HighPriorityMapper High priority mapper conversions
 * @groupprio HighPriorityMapper 1
 */
trait Mapper[A] {
  type Out

  def apply(r: Endpoint[A]): Endpoint[Out]
}

private[finch] trait LowPriorityMapperConversions {
  type Aux[A, B] = Mapper[A] { type Out = B }

  /**
    * @group LowPriorityMapper
    */
  implicit def mapperFromOutputFunction[A, B](f: A => Output[B]): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.mapOutput(f)
  }

  /**
    * @group LowPriorityMapper
    */
  implicit def mapperFromOutputFutureFunction[A, B](f: A => Output[Future[B]]): Mapper.Aux[A, B] =
    new Mapper[A] {
      type Out = B
      def apply(r: Endpoint[A]): Endpoint[Out] = r.mapOutputAsync(f.andThen(ofb => ofb.traverse(identity)))
    }

  /**
    * @group LowPriorityMapper
    */
  implicit def mapperFromFutureOutputFunction[A, B](f: A => Future[Output[B]]): Mapper.Aux[A, B] =
    new Mapper[A] {
      type Out = B
      def apply(r: Endpoint[A]): Endpoint[Out] = r.mapOutputAsync(f)
    }
}

private[finch] trait HighPriorityMapperConversions extends LowPriorityMapperConversions {

  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromOutputHFunction[A, B, F, OB](f: F)(implicit
    ftp: FnToProduct.Aux[F, A => OB],
    ev: OB <:< Output[B]
  ): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.mapOutput(value => ev(ftp(f)(value)))
  }

  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromOutputValue[A](o: => Output[A]): Mapper.Aux[HNil, A] = new Mapper[HNil] {
    type Out = A
    def apply(r: Endpoint[HNil]): Endpoint[Out] = r.mapOutput(_ => o)
  }

  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromOutputFutureValue[A](o: => Output[Future[A]]): Mapper.Aux[HNil, A] =
    new Mapper[HNil] {
      type Out = A
      def apply(r: Endpoint[HNil]): Endpoint[Out] = r.mapOutputAsync(_ => o.traverse(identity))
    }

  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromFutureOutputValue[A](o: => Future[Output[A]]): Mapper.Aux[HNil, A] =
    new Mapper[HNil] {
      type Out = A
      def apply(r: Endpoint[HNil]): Endpoint[Out] = r.mapOutputAsync(_ => o)
    }
}

object Mapper extends HighPriorityMapperConversions {
  implicit def mapperFromOutputFutureHFunction[A, B, F, OFB](f: F)(implicit
     ftp: FnToProduct.Aux[F, A => OFB],
     ev: OFB <:< Output[Future[B]]
  ): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] =
      r.mapOutputAsync(value => ev(ftp(f)(value)).traverse(identity))
  }

  implicit def mapperFromFutureOutputHFunction[A, B, F, FOB](f: F)(implicit
    ftp: FnToProduct.Aux[F, A => FOB],
    ev: FOB <:< Future[Output[B]]
  ): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(r: Endpoint[A]): Endpoint[Out] = r.mapOutputAsync(value => ev(ftp(f)(value)))
  }
}
