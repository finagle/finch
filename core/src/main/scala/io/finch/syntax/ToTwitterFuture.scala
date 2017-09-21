package io.finch.syntax

import cats.Monad
import com.twitter.util.Future
import io.catbird.util._

/**
  * Type class for conversion of some HKT (i.e. `scala.concurrent.Future`) to `com.twitter.util.Future`
  */
trait ToTwitterFuture[F[_]] {

  def M: Monad[F]
  def apply[A](f: F[A]): Future[A]

}

object ToTwitterFuture {

  implicit val identity: ToTwitterFuture[Future] = new ToTwitterFuture[Future] {

    val M: Monad[Future] = implicitly[Monad[Future]]

    def apply[A](f: Future[A]): Future[A] = f
  }

}
