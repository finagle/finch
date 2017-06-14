package io.finch.internal

import cats.Eq
import cats.laws.discipline.MonadErrorTests
import com.twitter.conversions.time._
import com.twitter.util.{Await, Future, Try}
import io.finch.FinchSpec
import org.scalacheck.Arbitrary

class RerunnableSpec extends FinchSpec {
  implicit def throwableEq: Eq[Throwable] = Eq.fromUniversalEquals

  implicit def rerunnableEq[A](implicit A: Eq[Try[A]]): Eq[Rerunnable[A]] = Eq.instance { (a, b) =>
    A.eqv(
      Await.result(a.liftToTry.run, 10.seconds),
      Await.result(b.liftToTry.run, 10.seconds)
    )
  }

  implicit def futureArbitrary[A](implicit A: Arbitrary[A]): Arbitrary[Future[A]] =
    Arbitrary(A.arbitrary.map(Future.value))

  implicit def rerunnableArbitrary[A](implicit A: Arbitrary[A]): Arbitrary[Rerunnable[A]] =
    Arbitrary(futureArbitrary[A].arbitrary.map(Rerunnable.fromFuture[A](_)))

  checkAll("Rerunnable[Int]", MonadErrorTests[Rerunnable, Throwable].monadError[Int, Int, Int])
}
