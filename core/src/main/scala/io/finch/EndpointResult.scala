package io.finch

import cats.effect.{Effect, IO}
import com.twitter.finagle.http.Method
import com.twitter.util._

/**
 * A result returned from an [[Endpoint]]. This models `Option[(Input, Future[Output])]` and
 * represents two cases:
 *
 *  - Endpoint is matched (think of 200).
 *  - Endpoint is not matched (think of 404, 405, etc).
 *
 * In its current state, `EndpointResult.NotMatched` represented with two cases:
 *
 *  - `EndpointResult.NotMatched` (very generic result usually indicating 404)
 *  - `EndpointResult.NotMatched.MethodNotAllowed` (indicates 405)
 *
 */
sealed abstract class EndpointResult[F[_], +A] {

  /**
   * Whether the [[Endpoint]] is matched on a given [[Input]].
   */
  def isMatched: Boolean

  /**
   * Returns the remainder of the [[Input]] after an [[Endpoint]] is matched.
   *
   * @return `Some(remainder)` if this endpoint was matched on a given input,
   *         `None` otherwise.
   */
  final def remainder: Option[Input] = this match {
    case EndpointResult.Matched(rem, _, _) => Some(rem)
    case _ => None
  }

  /**
   * Returns the [[Trace]] if an [[Endpoint]] is matched.
   *
   * @return `Some(trace)` if this endpoint is matched on a given input,
   *          `None` otherwise.
   */
  final def trace: Option[Trace] = this match {
    case EndpointResult.Matched(_, trc, _) => Some(trc)
    case _ => None
  }

  def awaitOutput(d: Duration = Duration.Top)(implicit e: Effect[F]): Option[Try[Output[A]]] = this match {
    case EndpointResult.Matched(_, _, out) =>
      val promise = Promise[Try[Output[A]]]
      val io = Effect[F].runAsync(out) {
        case Right(r) =>
          IO.pure(promise.setValue(Return(r)))
        case Left(t) =>
          IO.pure(promise.setValue(Throw(t)))
      }
      io.unsafeRunSync()
      Some(Await.result(promise, d))
    case _ => None
  }

  def awaitOutputUnsafe(d: Duration = Duration.Top)(implicit e: Effect[F]): Option[Output[A]] =
    awaitOutput(d).map(toa => toa.get)

  def awaitValue(d: Duration = Duration.Top)(implicit e: Effect[F]): Option[Try[A]] =
    awaitOutput(d).map(toa => toa.flatMap(oa => Try(oa.value)))

  def awaitValueUnsafe(d: Duration = Duration.Top)(implicit e: Effect[F]): Option[A] =
    awaitOutputUnsafe(d).map(oa => oa.value)
}

object EndpointResult {

  final case class Matched[F[_], A](
    rem: Input,
    trc: Trace,
    out: F[Output[A]]
  ) extends EndpointResult[F, A] {
    def isMatched: Boolean = true
  }

  abstract class NotMatched[F[_]] extends EndpointResult[F, Nothing] {
    def isMatched: Boolean = false
  }

  object NotMatched extends NotMatched[Pure] {
    final case class MethodNotAllowed[F[_]](allowed: List[Method]) extends NotMatched[F]
  }

  implicit def covaryEndpointResult[F[_], A](result: EndpointResult[Pure, A]): EndpointResult[F, A] =
    result.asInstanceOf[EndpointResult[F, A]]
}

