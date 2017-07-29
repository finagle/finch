package io.finch.syntax.scala

import com.twitter.finagle.http.Response
import io.finch.Output
import io.finch.internal.ScalaToTwitterConversions._
import io.finch.syntax.{HighPriorityMapperConversions, LowPriorityMapperConversions, Mapper, TwitterMapper}
import scala.concurrent.ExecutionContext
import shapeless.HNil
import shapeless.ops.function.FnToProduct

private[finch] trait ScalaLowPriorityMapperConversions extends LowPriorityMapperConversions{
  /**
    * @group LowPriorityMapper
    */
  implicit def mapperFromScalaFutureOutputFunction[A, B](f: A => scala.concurrent.Future[Output[B]])
                                                   (implicit ec: ExecutionContext): Mapper.Aux[A, B] =
    instance(_.mapOutputAsync {x => f(x).asTwitterFuture} )

  /**
    * @group LowPriorityMapper
    */
  implicit def mapperFromScalaFutureResponseFunction[A](f: A => scala.concurrent.Future[Response])
                                                  (implicit ec: ExecutionContext) : Mapper.Aux[A, Response] =
    instance(_.mapOutputAsync(f.andThen(fr => fr.map(r => Output.payload(r, r.status)).asTwitterFuture)))
}

private[finch] trait ScalaHighPriorityMapperConversions
  extends HighPriorityMapperConversions
    with ScalaLowPriorityMapperConversions{

  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromScalaFutureOutputValue[A]
  (o: => scala.concurrent.Future[Output[A]])
  (implicit ec: ExecutionContext)
  : Mapper.Aux[HNil, A] =
    instance(_.mapOutputAsync(_ => o.asTwitterFuture))

  /**
    * @group HighPriorityMapper
    */
  implicit def mapperFromScalaFutureResponseValue
  (fr: => scala.concurrent.Future[Response])
  (implicit ec: ExecutionContext)
  : Mapper.Aux[HNil, Response] =
    instance(_.mapOutputAsync(_ => fr.map(r => Output.payload(r, r.status)).asTwitterFuture))
}

private[finch] trait ScalaMapper extends TwitterMapper with ScalaHighPriorityMapperConversions {
  implicit def mapperFromScalaFutureOutputHFunction[A, B, F, FOB](f: F)(implicit
                                                                        ftp: FnToProduct.Aux[F, A => FOB],
                                                                        ev: FOB <:< scala.concurrent.Future[Output[B]],
                                                                        ec: ExecutionContext)
  : Mapper.Aux[A, B] = instance(_.mapOutputAsync(value => ev(ftp(f)(value)).asTwitterFuture))


  implicit def mapperFromScalaFutureResponseHFunction[A, F, FR](f: F)
                                                               (implicit ftp: FnToProduct.Aux[F, A => FR],
                                                                ev: FR <:< scala.concurrent.Future[Response],
                                                                ec: ExecutionContext)
  : Mapper.Aux[A, Response] = instance(_.mapOutputAsync { value =>
    val fr = ev(ftp(f)(value)).asTwitterFuture
    fr.map(r => Output.payload(r, r.status))
  })
}
