package io.finch.syntax

import com.twitter.finagle.http.Response
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
  implicit def mapperFromFutureOutputFunction[A, B](f: A => Future[Output[B]]): Mapper.Aux[A, B] =
    instance(_.mapOutputAsync(f))

  /**
   * @group LowPriorityMapper
   */
  implicit def mapperFromFutureResponseFunction[A](f: A => Future[Response]): Mapper.Aux[A, Response] =
    instance(_.mapOutputAsync(f.andThen(fr => fr.map(r => Output.payload(r, r.status)))))
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
  implicit def mapperFromFutureOutputValue[A](o: => Future[Output[A]]): Mapper.Aux[HNil, A] =
    instance(_.mapOutputAsync(_ => o))

  /**
   * @group HighPriorityMapper
   */
  implicit def mapperFromFutureResponseValue(fr: => Future[Response]): Mapper.Aux[HNil, Response] =
    instance(_.mapOutputAsync(_ => fr.map(r => Output.payload(r, r.status))))
}

object Mapper extends HighPriorityMapperConversions {
  implicit def mapperFromFutureOutputHFunction[A, B, F, FOB](f: F)(implicit
    ftp: FnToProduct.Aux[F, A => FOB],
    ev: FOB <:< Future[Output[B]]
  ): Mapper.Aux[A, B] = instance(_.mapOutputAsync(value => ev(ftp(f)(value))))

  implicit def mapperFromFutureResponseHFunction[A, F, FR](f: F)(implicit
    ftp: FnToProduct.Aux[F, A => FR],
    ev: FR <:< Future[Response]
  ): Mapper.Aux[A, Response] = instance(_.mapOutputAsync { value =>
    val fr = ev(ftp(f)(value))
    fr.map(r => Output.payload(r, r.status))
  })
}
