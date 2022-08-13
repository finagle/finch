package io.finch.internal

import cats.syntax.functor._
import cats.{Monad, MonadThrow}
import com.twitter.finagle.http.Response
import io.finch.{Endpoint, Output}
import shapeless.HNil
import shapeless.ops.function.FnToProduct

/** A type class that allows the [[Endpoint]] to be mapped to either `A => B` or `A => Future[B]`.
  * @groupname LowPriorityMapper Low Priority Mapper Conversions
  * @groupprio LowPriorityMapper 0
  * @groupname HighPriorityMapper High priority mapper conversions
  * @groupprio HighPriorityMapper 1
  */
trait Mapper[F[_], A] {
  type Out

  /** @param e
    *   The endpoint to map
    * @tparam X
    *   Hack to stop the compiler from converting this to a SAM
    * @return
    *   An endpoint that returns an `Out`
    */
  def apply[X](e: Endpoint[F, A]): Endpoint[F, Out]
}

private[finch] trait LowPriorityMapperConversions {
  type Aux[F[_], A, B] = Mapper[F, A] {
    type Out = B
  }

  def instance[F[_], A, B](f: Endpoint[F, A] => Endpoint[F, B]): Aux[F, A, B] =
    new Mapper[F, A] {
      type Out = B
      def apply[X](e: Endpoint[F, A]): Endpoint[F, Out] = f(e)
    }

  /** @group LowPriorityMapper */
  implicit def mapperFromOutputFunction[F[_]: MonadThrow, A, B](f: A => Output[B]): Aux[F, A, B] =
    instance(_.mapOutput(f))

  /** @group LowPriorityMapper */
  implicit def mapperFromResponseFunction[F[_]: MonadThrow, A](f: A => Response): Aux[F, A, Response] =
    instance(_.mapOutput(f.andThen(r => Output.payload(r, r.status))))
}

private[finch] trait HighPriorityMapperConversions extends LowPriorityMapperConversions {

  /** @group HighPriorityMapper */
  implicit def mapperFromOutputHFunction[F[_]: MonadThrow, A, B, FN, OB](f: FN)(implicit
      ftp: FnToProduct.Aux[FN, A => OB],
      ev: OB <:< Output[B]
  ): Aux[F, A, B] =
    instance(_.mapOutput(value => ev(ftp(f)(value))))

  /** @group HighPriorityMapper */
  implicit def mapperFromResponseHFunction[F[_]: MonadThrow, A, FN, R](f: FN)(implicit
      ftp: FnToProduct.Aux[FN, A => R],
      ev: R <:< Response
  ): Aux[F, A, Response] = instance(_.mapOutput { value =>
    val r = ev(ftp(f)(value))
    Output.payload(r, r.status)
  })

  /** @group HighPriorityMapper */
  implicit def mapperFromOutputValue[F[_]: MonadThrow, A](o: => Output[A]): Aux[F, HNil, A] =
    instance(_.mapOutput(_ => o))

  /** @group HighPriorityMapper */
  implicit def mapperFromResponseValue[F[_]: MonadThrow](r: => Response): Aux[F, HNil, Response] =
    instance(_.mapOutput(_ => Output.payload(r, r.status)))

  implicit def mapperFromKindToEffectOutputFunction[A, B, F[_]: Monad](f: A => F[Output[B]]): Aux[F, A, B] =
    instance(_.mapOutputAsync(a => f(a)))

  implicit def mapperFromKindToEffectOutputValue[A, B, F[_]: Monad](f: => F[Output[B]]): Aux[F, A, B] =
    instance(_.mapOutputAsync(_ => f))

  implicit def mapperFromKindToEffectResponseFunction[A, F[_]: Monad](f: A => F[Response]): Aux[F, A, Response] =
    instance(_.mapOutputAsync(f.andThen(_.map(r => Output.payload(r, r.status)))))

  implicit def mapperFromKindToEffectResponseValue[A, F[_]: Monad](f: => F[Response]): Aux[F, A, Response] =
    instance(_.mapOutputAsync(_ => f.map(r => Output.payload(r, r.status))))
}

object Mapper extends HighPriorityMapperConversions {

  implicit def mapperFromKindOutputHFunction[F[_]: Monad, A, B, FN, FOB](f: FN)(implicit
      ftp: FnToProduct.Aux[FN, A => FOB],
      ev: FOB <:< F[Output[B]]
  ): Aux[F, A, B] =
    instance(_.mapOutputAsync(a => ev(ftp(f)(a))))

  implicit def mapperFromKindResponseHFunction[F[_]: Monad, A, FN, FR](f: FN)(implicit
      ftp: FnToProduct.Aux[FN, A => FR],
      ev: FR <:< F[Response]
  ): Aux[F, A, Response] = instance(_.mapOutputAsync { value =>
    ev(ftp(f)(value)).map(r => Output.payload(r, r.status))
  })
}
