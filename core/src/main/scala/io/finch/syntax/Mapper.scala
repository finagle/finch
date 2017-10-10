package io.finch.syntax

import _root_.scala.annotation.implicitNotFound
import cats.Id
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
@implicitNotFound(
  """"A value you're trying to apply to Endpoint is missing Mapper instance.

  Make sure ${A} is one of the following:

  * A com.twitter.finagle.http.Response or io.finch.Output
  * A com.twitter.util.Future[Response] (or Output)
  * A value of type F[Response] or F[Output[A]] with implicit `io.finch.syntax.ToTwitterFuture` instance
  * A function with sufficient amount of arguments that returns one of the values above

  See https://github.com/finagle/finch/blob/master/docs/cookbook.md#mapper-syntax
  """")
trait Mapper[F, A] {
  type Out

  def apply(f: => F, e: Endpoint[A]): Endpoint[Out]
}

private[finch] trait LowPriorityMapperConversions {
  type Aux[F, A, B] = Mapper[F, A] { type Out = B }

  def instance[F, A, B](fn: (Endpoint[A], => F) => Endpoint[B]): Mapper.Aux[F, A, B] = new Mapper[F, A] {
    type Out = B

    def apply(f: => F, e: Endpoint[A]): Endpoint[B] = fn(e, f)
  }

  /**
    * @group LowPriorityMapper
    */
  implicit def mapperFromOutputFunction[A, B]: Mapper.Aux[A => Output[B], A, B] = instance((e, f) => e.mapOutput(f))

  /**
    * @group LowPriorityMapper
    */
  implicit def mapperFromResponseFunction[A]: Mapper.Aux[A => Response, A, Response] =
    instance((e, f) => e.mapOutput(f.andThen(r => Output.payload(r, r.status))))

  /**
    * @group LowPriorityMapper
    */
  implicit def mapperFromHOutputFunction[F[_], A, B](implicit
    ttf: ToTwitterFuture[F]
  ): Mapper.Aux[A => F[Output[B]], A, B] = instance((e, f) => e.mapOutputAsync(f.andThen(ttf.apply)))

  /**
    * @group LowPriorityMapper
    */
  implicit def mapperFromHResponseFunction[F[_], A](implicit
    ttf: ToTwitterFuture[F]
  ): Mapper.Aux[A => F[Response], A, Response] =
    instance((e, f) => e.mapOutputAsync(f.andThen(ttf.apply).andThen(fr => fr.map(r => Output.payload(r, r.status)))))
}

private[finch] trait HighPriorityMapperConversions extends LowPriorityMapperConversions {

  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromOutputHFunction[A, B, F, OB](implicit
   ftp: FnToProduct.Aux[F, A => OB],
   ev: OB <:< Id[Output[B]]
  ): Mapper.Aux[F, A, B] = instance((e, f) => e.mapOutput[B](value => ev(ftp(f)(value))))


  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromResponseHFunction[A, F, R](implicit
    ftp: FnToProduct.Aux[F, A => R],
    ev: R <:< Response
  ): Mapper.Aux[F, A, Response] = instance((e, f) => e.mapOutput { value =>
    val r = ev(ftp(f)(value))
    Output.payload(r, r.status)
  })

  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromOutputValue[A]: Mapper.Aux[Output[A], HNil, A] = instance((e, o) => e.mapOutput(_ => o))

  /**
    * @group HighPriorityMapper
    */
  implicit val mapperFromResponseValue: Mapper.Aux[Response, HNil, Response] =
    instance((e, r) => e.mapOutput(_ => Output.payload(r, r.status)))

  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromHOutputValue[F[_], A](implicit
    ttf: ToTwitterFuture[F]
  ): Mapper.Aux[F[Output[A]], HNil, A] = instance((e, f) => e.mapOutputAsync(_ => ttf(f)))

  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromHResponseValue[F[_]](implicit
    ttf: ToTwitterFuture[F]
  ): Mapper.Aux[F[Response], HNil, Response] =
    instance((e, f) => e.mapOutputAsync(_ => ttf(f).map(r => Output.payload(r, r.status))))
}

object Mapper extends HighPriorityMapperConversions {
  implicit def mapperFromHOutputHFunction[F[_], A, B, FN, FOB](implicit
    ftp: FnToProduct.Aux[FN, A => FOB],
    ev: FOB <:< F[Output[B]],
    ttf: ToTwitterFuture[F]
  ): Mapper.Aux[FN, A, B] = instance((e, f) => e.mapOutputAsync(value => ttf(ev(ftp(f)(value)))))

  implicit def mapperFromHResponseHFunction[F[_], A, FN, FR](implicit
    ftp: FnToProduct.Aux[FN, A => FR],
    ev: FR <:< F[Response],
    ttf: ToTwitterFuture[F]
  ): Mapper.Aux[FN, A, Response] = instance((e, f) => e.mapOutputAsync { value =>
    val fr = ttf(ev(ftp(f)(value)))
    fr.map(r => Output.payload(r, r.status))
  })
}