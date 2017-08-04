package io.finch.syntax

import com.twitter.util.Future

trait ToTwitterFuture[F[_]] {

  def apply[A](f: F[A]): Future[A]

}

object ToTwitterFuture {

  implicit val identity: ToTwitterFuture[Future] = new ToTwitterFuture[Future] {
    override def apply[A](f: Future[A]): Future[A] = f
  }

}
