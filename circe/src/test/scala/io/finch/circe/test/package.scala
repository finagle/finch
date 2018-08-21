package io.finch.circe

import cats.Comonad
import com.twitter.util.Try

package object test {

  implicit val comonadEither: Comonad[Try] = new Comonad[Try] {
    def extract[A](x: Try[A]): A = x.get() //never do it in production, kids

    def coflatMap[A, B](fa: Try[A])(f: Try[A] => B): Try[B] = Try(f(fa))

    def map[A, B](fa: Try[A])(f: A => B): Try[B] = fa.map(f)
  }

}
