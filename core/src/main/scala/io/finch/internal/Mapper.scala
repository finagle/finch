package io.finch.internal

import cats.Monad
import cats.effect.Effect
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

  implicit def mapperFromKindToEffectOutputFunction[A, B, F[_], G[_]: Effect](f: A => F[Output[B]])(
    implicit conv: ToEffect[F, G]): Mapper.Aux[G, A, B] =
    instance(_.mapOutputAsync(a => conv.apply(f(a))))

  implicit def mapperFromKindToEffectOutputValue[A, B, F[_], G[_]: Effect](f: => F[Output[B]])(
    implicit conv: ToEffect[F, G]): Mapper.Aux[G, A, B] = instance(_.mapOutputAsync(a => conv.apply(f)))

  implicit def mapperFromKindToEffectResponsFunction[A, F[_], G[_]: Effect](f: A => F[Response])(
    implicit conv: ToEffect[F, G]): Mapper.Aux[G, A, Response] =
    instance(_.mapOutputAsync(f.andThen(fr => conv(fr).map(r => Output.payload(r, r.status)))))

  implicit def mapperFromKindToEffectResponseValue[A, F[_], G[_]: Effect](f: => F[Response])(
    implicit conv: ToEffect[F, G]): Mapper.Aux[G, A, Response] =
    instance(_.mapOutputAsync(_=>conv(f).map(r => Output.payload(r, r.status))))
}

object Mapper extends HighPriorityMapperConversions {

  implicit def mapperFromKindOutputHFunction[F[_]: Effect, G[_], A, B, FN, FOB](f: FN)(implicit
    ftp: FnToProduct.Aux[FN, A => FOB],
    ev: FOB <:< G[Output[B]],
    conv: ToEffect[G, F]
  ): Mapper.Aux[F, A, B] =
    instance(_.mapOutputAsync(a => conv.apply(ev(ftp(f)(a)))))

  implicit def mapperFromKindResponseHFunction[F[_] : Effect, G[_], A, FN, FR](f: FN)(implicit
    ftp: FnToProduct.Aux[FN, A => FR],
    ev: FR <:< G[Response],
    conv: ToEffect[G, F]
  ): Mapper.Aux[F, A, Response] = instance(_.mapOutputAsync { value =>
    val fr = conv(ev(ftp(f)(value)))
    fr.map(r => Output.payload(r, r.status))
  })
}
