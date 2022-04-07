package io.finch.internal

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture}

import cats.Applicative
import cats.effect.IO
import com.twitter.util.{Future => TwitterFuture}
import io.finch.FinchSpec

class ToEffectSpec extends FinchSpec {

  implicit val twitterFutureApplicative: Applicative[TwitterFuture] = new Applicative[TwitterFuture] {
    def pure[A](x: A): TwitterFuture[A] = TwitterFuture.value(x)
    def ap[A, B](ff: TwitterFuture[A => B])(fa: TwitterFuture[A]): TwitterFuture[B] =
      fa.flatMap(a => ff.map(_.apply(a)))
  }

  checkAll("ToEffect[TwitterFuture, IO]", ToEffectLaws.apply[TwitterFuture, IO, Int](_.unsafeRunSync()).all)
  checkAll("ToEffect[ScalaFuture, IO]", ToEffectLaws[ScalaFuture, IO, Int](_.unsafeRunSync()).all)
  checkAll("ToEffect[IO, IO]", ToEffectLaws[IO, IO, Int](_.unsafeRunSync()).all)

}
