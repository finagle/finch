package io.finch.internal

import cats.Monad
import cats.effect.Effect
import cats.syntax.functor._
import com.twitter.finagle.http.Response
import com.twitter.util.{Future => TwitterFuture, Return, Throw}
import io.finch.{Endpoint, Output}
import scala.concurrent.{Future => ScalaFuture}
import scala.util.{Failure, Success}
import shapeless.HNil
import shapeless.ops.function.FnToProduct
import cats.~>
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

  implicit val twitterFutureToEffect[F[_]: Effect] = new (TwitterFuture ~> F) {
    def apply[A](f: => TwitterFuture[A]): F[A] = {
      implicitly[Effect[F]].async { cb =>
        f.respond {
          case Return(r) => cb(Right(r))
          case Throw(t) => cb(Left(t))
        }
      }
    }
  }

  implicit val scalaFutureToEffect[F[_]: Effect] = new (ScalaFuture ~> F) {
    def apply[A](f: => ScalaFuture[A]): F[A] = {
      implicitly[Effect[F]].async { cb =>
        f.onComplete {
          case Success(s) => cb(Right(s))
          case Failure(t) => cb(Left(t))
        }(DummyExecutionContext)
      }
    }
  }

  implicit def mapperToEffectOutputFunction[A, B, F[_], G[_]:Effect](f: A => F[Output[B]])(implicit cov: F ~> G): Mapper.Aux[G, A, B] =
    instance(_.mapOutputAsync(a => cov.apply(f(a))))

  implicit def mapperToEffectOutputValue[A, B, F[_], G[_]:Effect](f: => F[Output[B]])(implicit cov: F ~> G): Mapper.Aux[G, A, B] = instance(_.mapOutputAsync(a => cov.apply(f)))
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

  /**
    * @group LowPriorityMapper
    */
  @deprecated("com.twitter.util.Future use is deprecated in Endpoints. Consider to use cats-effect compatible effect", "0.25.0")
  implicit def mapperFromFutureResponseFunction[A, F[_] : Effect](f: A => TwitterFuture[Response]): Mapper.Aux[F, A, Response] =
    instance(_.mapOutputAsync(f.andThen(fr => twitterFutureToEffect(fr).map(r => Output.payload(r, r.status)))))

  /**
    * @group LowPriorityMapper
    */
  @deprecated("scala.concurrent.Future use is deprecated in Endpoints. Consider to use cats-effect compatible effect", "0.25.0")
  implicit def mapperFromScFutureResponseFunction[A, F[_] : Effect](f: A => ScalaFuture[Response]): Mapper.Aux[F, A, Response] =
    instance(_.mapOutputAsync(f.andThen(fr => scalaFutureToEffect(fr).map(r => Output.payload(r, r.status)))))
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

  /**
    * @group HighPriorityMapper
    */
  @deprecated("com.twitter.util.Future use is deprecated in Endpoints. Consider to use cats-effect compatible effect", "0.25.0")
  implicit def mapperFromFutureResponseValue[F[_] : Effect](fr: => TwitterFuture[Response]): Mapper.Aux[F, HNil, Response] =
    instance(_.mapOutputAsync(_ => twitterFutureToEffect(fr).map(r => Output.payload(r, r.status))))

  /**
    * @group HighPriorityMapper
    */
  @deprecated("scala.concurrent.Future use is deprecated in Endpoints. Consider to use cats-effect compatible effect", "0.25.0")
  implicit def mapperFromScFutureResponseValue[F[_] : Effect](fr: => ScalaFuture[Response]): Mapper.Aux[F, HNil, Response] =
    instance(_.mapOutputAsync(_ => scalaFutureToEffect(fr).map(r => Output.payload(r, r.status))))
}

object Mapper extends HighPriorityMapperConversions {
  implicit def mapperFromKindOutputHFunction[F[_] : Effect, G[_], A, B, FN, FOB](f: FN)(implicit
    conv: G ~> F,
    ftp: FnToProduct.Aux[FN, A => FOB],
    ev: FOB <:< G[Output[B]]
  ): Mapper.Aux[F, A, B] = instance(_.mapOutputAsync(a => conv.apply(ev(ftp(f)(a)))))

  implicit def mapperFromEffectOutputHFunction[F[_] : Monad, A, B, FN, FOB](f: FN)(implicit
    ftp: FnToProduct.Aux[FN, A => FOB],
    ev: FOB <:< F[Output[B]]
  ): Mapper.Aux[F, A, B] = instance(_.mapOutputAsync(value => ev(ftp(f)(value))))

  implicit def mapperFromEffectResponseHFunction[F[_] : Monad, A, FN, FR](f: FN)(implicit
    ftp: FnToProduct.Aux[FN, A => FR],
    ev: FR <:< F[Response]
  ): Mapper.Aux[F, A, Response] = instance(_.mapOutputAsync { value =>
    val fr = ev(ftp(f)(value))
    fr.map(r => Output.payload(r, r.status))
  })

  @deprecated("com.twitter.util.Future use is deprecated in Endpoints. Consider to use cats-effect compatible effect", "0.25.0")
  implicit def mapperFromFutureOutputHFunction[F[_] : Effect, A, B, FN, FOB](f: FN)(implicit
    ftp: FnToProduct.Aux[FN, A => FOB],
    ev: FOB <:< TwitterFuture[Output[B]]
  ): Mapper.Aux[F, A, B] = instance(_.mapOutputAsync(value => twitterFutureToEffect(ev(ftp(f)(value)))))

  @deprecated("com.twitter.util.Future use is deprecated in Endpoints. Consider to use cats-effect compatible effect", "0.25.0")
  implicit def mapperFromFutureResponseHFunction[F[_] : Effect, A, FN, FR](f: FN)(implicit
    ftp: FnToProduct.Aux[FN, A => FR],
    ev: FR <:< TwitterFuture[Response]
  ): Mapper.Aux[F, A, Response] = instance(_.mapOutputAsync { value =>
    val fr = twitterFutureToEffect(ev(ftp(f)(value)))
    fr.map(r => Output.payload(r, r.status))
  })

  @deprecated("scala.concurrent.Future use is deprecated in Endpoints. Consider to use cats-effect compatible effect", "0.25.0")
  implicit def mapperFromScFutureOutputHFunction[F[_] : Effect, A, B, FN, FOB](f: FN)(implicit
    ftp: FnToProduct.Aux[FN, A => FOB],
    ev: FOB <:< ScalaFuture[Output[B]]
  ): Mapper.Aux[F, A, B] = instance(_.mapOutputAsync(value => scalaFutureToEffect(ev(ftp(f)(value)))))

  @deprecated("scala.concurrent.Future use is deprecated in Endpoints. Consider to use cats-effect compatible effect", "0.25.0")
  implicit def mapperFromScFutureResponseHFunction[F[_] : Effect, A, FN, FR](f: FN)(implicit
    ftp: FnToProduct.Aux[FN, A => FR],
    ev: FR <:< ScalaFuture[Response]
  ): Mapper.Aux[F, A, Response] = instance(_.mapOutputAsync { value =>
    val fr = scalaFutureToEffect(ev(ftp(f)(value)))
    fr.map(r => Output.payload(r, r.status))
  })
}
