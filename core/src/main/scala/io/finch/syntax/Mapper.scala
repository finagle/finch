package io.finch.syntax

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
trait Mapper[A] {
  type Out

  def apply(e: Endpoint[A]): Endpoint[Out]
}

private[finch] trait LowPriorityMapperConversions {
  type Aux[A, B] = Mapper[A] { type Out = B }

  def instance[A, B](f: Endpoint[A] => Endpoint[B]): Mapper.Aux[A, B] = new Mapper[A] {
    type Out = B
    def apply(e: Endpoint[A]): Endpoint[B] = f(e)
  }

  /**
    * @group LowPriorityMapper
    */
  implicit def mapperFromOutputFunction[A, B](f: A => Output[B]): Mapper.Aux[A, B] =
    instance(_.mapOutput(f))

  /**
   * @group LowPriorityMapper
   */
  implicit def mapperFromResponseFunction[A](f: A => Response): Mapper.Aux[A, Response] =
    instance(_.mapOutput(f.andThen(r => Output.payload(r, r.status))))

  /**
    * @group LowPriorityMapper
    */
  implicit def mapperFromFutureOutputFunction[A, B, H[_]](f: A => H[Output[B]])(implicit
    ttf: ToTwitterFuture[H]
  ): Mapper.Aux[A, B] =
    instance(_.mapOutputAsync(f.andThen(ttf.apply)))

  /**
   * @group LowPriorityMapper
   */
  implicit def mapperFromFutureResponseFunction[A, H[_]](f: A => H[Response])(implicit
    ttf: ToTwitterFuture[H]
  ): Mapper.Aux[A, Response] =
    instance(_.mapOutputAsync(f.andThen(ttf.apply).andThen(fr => fr.map(r => Output.payload(r, r.status)))))
}

private[finch] trait HighPriorityMapperConversions extends LowPriorityMapperConversions {

  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromOutputHFunction[A, B, F, OB](f: F)(implicit
    ftp: FnToProduct.Aux[F, A => OB],
    ev: OB <:< Output[B]
  ): Mapper.Aux[A, B] = instance(_.mapOutput(value => ev(ftp(f)(value))))


  /**
   * @group HighPriorityMapper
   */
  implicit def mapperFromResponseHFunction[A, F, R](f: F)(implicit
    ftp: FnToProduct.Aux[F, A => R],
    ev: R <:< Response
  ): Mapper.Aux[A, Response] = instance(_.mapOutput { value =>
    val r = ev(ftp(f)(value))
    Output.payload(r, r.status)
  })

  /**
   * @group HighPriorityMapper
   */
  implicit def mapperFromOutputValue[A](o: => Output[A]): Mapper.Aux[HNil, A] =
    instance(_.mapOutput(_ => o))

  /**
   * @group HighPriorityMapper
   */
  implicit def mapperFromResponseValue(r: => Response): Mapper.Aux[HNil, Response] =
    instance(_.mapOutput(_ => Output.payload(r, r.status)))

  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromFutureOutputValue[A, H[_]](o: => H[Output[A]])(implicit
    ttf: ToTwitterFuture[H]
  ): Mapper.Aux[HNil, A] =
    instance(_.mapOutputAsync(_ => ttf(o)))

  /**
   * @group HighPriorityMapper
   */
  implicit def mapperFromFutureResponseValue[H[_]](fr: => H[Response])(implicit
    ttf: ToTwitterFuture[H]
  ): Mapper.Aux[HNil, Response] = instance(_.mapOutputAsync(_ => ttf(fr).map(r => Output.payload(r, r.status))))
}

object Mapper extends HighPriorityMapperConversions {
  implicit def mapperFromFutureOutputHFunction[A, B, F, FOB, H[_]](f: F)(implicit
    ftp: FnToProduct.Aux[F, A => FOB],
    ev: FOB <:< H[Output[B]],
    ttf: ToTwitterFuture[H]
  ): Mapper.Aux[A, B] = instance(_.mapOutputAsync(value => ttf(ev(ftp(f)(value)))))

  implicit def mapperFromFutureResponseHFunction[A, F, FR, H[_]](f: F)(implicit
    ftp: FnToProduct.Aux[F, A => FR],
    ev: FR <:< H[Response],
    ttf: ToTwitterFuture[H]
  ): Mapper.Aux[A, Response] = instance(_.mapOutputAsync { value =>
    val fr = ttf(ev(ftp(f)(value)))
    fr.map(r => Output.payload(r, r.status))
  })
}
