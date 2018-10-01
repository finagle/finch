package io.finch.internal

import cats.Monad
import cats.syntax.functor._
import com.twitter.finagle.http.Response
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
trait Mapper[F[_], A] {
  type Out

  def apply(e: Endpoint[F, A]): Endpoint[F, Out]
}

private[finch] trait LowPriorityMapperConversions {
  type Aux[F[_], A, B] = Mapper[F, A] { type Out = B }

  def instance[F[_], A, B](f: Endpoint[F, A] => Endpoint[F, B]): Mapper.Aux[F, A, B] = new Mapper[F, A] {
    type Out = B
    def apply(e: Endpoint[F, A]): Endpoint[F, B] = f(e)
  }

  /**
    * @group LowPriorityMapper
    */
  implicit def mapperFromOutputFunction[F[_] : Monad, A, B](f: A => Output[B]): Mapper.Aux[F, A, B] =
    instance(_.mapOutput(f))

  /**
   * @group LowPriorityMapper
   */
  implicit def mapperFromResponseFunction[F[_] : Monad, A](f: A => Response): Mapper.Aux[F, A, Response] =
    instance(_.mapOutput(f.andThen(r => Output.payload(r, r.status))))

  /**
    * @group LowPriorityMapper
    */
  implicit def mapperFromEffectOutputFunction[A, B, F[_] : Monad](f: A => F[Output[B]]): Mapper.Aux[F, A, B] =
    instance(_.mapOutputAsync(f))

  /**
   * @group LowPriorityMapper
   */
  implicit def mapperFromEffectResponseFunction[A, F[_] : Monad](f: A => F[Response]): Mapper.Aux[F, A, Response] =
    instance(_.mapOutputAsync(f.andThen(fr => fr.map(r => Output.payload(r, r.status)))))
}

private[finch] trait HighPriorityMapperConversions extends LowPriorityMapperConversions {

  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromOutputHFunction[F[_] : Monad, A, B, FN, OB](f: FN)(implicit
    ftp: FnToProduct.Aux[FN, A => OB],
    ev: OB <:< Output[B]
  ): Mapper.Aux[F, A, B] = instance(_.mapOutput(value => ev(ftp(f)(value))))


  /**
   * @group HighPriorityMapper
   */
  implicit def mapperFromResponseHFunction[F[_] : Monad, A, FN, R](f: FN)(implicit
    ftp: FnToProduct.Aux[FN, A => R],
    ev: R <:< Response
  ): Mapper.Aux[F, A, Response] = instance(_.mapOutput { value =>
    val r = ev(ftp(f)(value))
    Output.payload(r, r.status)
  })

  /**
   * @group HighPriorityMapper
   */
  implicit def mapperFromOutputValue[F[_] : Monad, A](o: => Output[A]): Mapper.Aux[F, HNil, A] =
    instance(_.mapOutput(_ => o))

  /**
   * @group HighPriorityMapper
   */
  implicit def mapperFromResponseValue[F[_] : Monad](r: => Response): Mapper.Aux[F, HNil, Response] =
    instance(_.mapOutput(_ => Output.payload(r, r.status)))

  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromEffectOutputValue[F[_] : Monad, A](o: F[Output[A]]): Mapper.Aux[F, HNil, A] =
    instance(_.mapOutputAsync(_ => o))

  /**
   * @group HighPriorityMapper
   */
  implicit def mapperFromEffectResponseValue[F[_] : Monad](fr: F[Response]): Mapper.Aux[F, HNil, Response] =
    instance(_.mapOutputAsync(_ => fr.map(r => Output.payload(r, r.status))))
}

object Mapper extends HighPriorityMapperConversions {
  implicit def mapperFromEffectOutputHFunction[F[_] : Monad, A, B, FN, FOB](f: FN)(implicit
    ftp: FnToProduct.Aux[FN, A => FOB],
    ev: FOB <:< F[Output[B]]
  ): Mapper.Aux[F, A, B] = instance(_.mapOutputAsync(value => ev(ftp(f)(value))))

  implicit def mapperFromFutureResponseHFunction[F[_] : Monad, A, FN, FR](f: FN)(implicit
    ftp: FnToProduct.Aux[FN, A => FR],
    ev: FR <:< F[Response]
  ): Mapper.Aux[F, A, Response] = instance(_.mapOutputAsync { value =>
    val fr = ev(ftp(f)(value))
    fr.map(r => Output.payload(r, r.status))
  })
}
