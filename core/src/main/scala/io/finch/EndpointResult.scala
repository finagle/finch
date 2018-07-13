package io.finch

import arrows.twitter.Task
import com.twitter.finagle.http.Method
import com.twitter.util.{Await, Duration, Future, Try}
import io.catbird.util.Rerunnable

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
 * API methods exposed on this type are mostly introduced for testing.
 *
 * This class also provides various of `awaitX` methods useful for testing and experimenting.
 */
sealed abstract class EndpointResult[+A] {

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
   * Runs and returns the [[Output]] (wrapped with future) after an [[Endpoint]] is matched.
   *
   * @return `Some(output)` if this endpoint was matched on a given input,
   *         `None` otherwise.
   */
  final def output: Option[Future[Output[A]]] = this match {
    case EndpointResult.Matched(_, _, out) => Some(out.run)
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

  /**
   * Awaits for an [[Output]] wrapped with [[Try]] (indicating if the [[com.twitter.util.Future]]
   * is failed).
   *
   * @note This method is blocking. Never use it in production.
   *
   * @return `Some(output)` if this endpoint was matched on a given input, `None` otherwise.
   */
  final def awaitOutput(d: Duration = Duration.Top): Option[Try[Output[A]]] = this match {
    case EndpointResult.Matched(_, _, out) => Some(Await.result(out.liftToTry.run, d))
    case _ => None
  }

  /**
   * Awaits an [[Output]] of the [[Endpoint]] result or throws an exception if an underlying
   * [[com.twitter.util.Future]] is failed.
   *
   * @note This method is blocking. Never use it in production.
   *
   * @return `Some(output)` if this endpoint was matched on a given input, `None` otherwise.
   */
  final def awaitOutputUnsafe(d: Duration = Duration.Top): Option[Output[A]] =
    awaitOutput(d).map(toa => toa.get)

  /**
   * Awaits a value from the [[Output]] wrapped with [[Try]] (indicating if either the
   * [[com.twitter.util.Future]] is failed or [[Output]] wasn't a payload).
   *
   * @note This method is blocking. Never use it in production.
   *
   * @return `Some(value)` if this endpoint was matched on a given input, `None` otherwise.
   */
  final def awaitValue(d: Duration = Duration.Top): Option[Try[A]] =
    awaitOutput(d).map(toa => toa.flatMap(oa => Try(oa.value)))

  /**
   * Awaits a value from the [[Output]] or throws an exception if either an underlying
   * [[com.twitter.util.Future]] is failed or [[Output]] wasn't a payload.
   *
   * @note @note This method is blocking. Never use it in production.
   *
   * @return `Some(value)` if this endpoint was matched on a given input,
   *         `None` otherwise.
   */
  final def awaitValueUnsafe(d: Duration = Duration.Top): Option[A] =
    awaitOutputUnsafe(d).map(oa => oa.value)
}

object EndpointResult {

  final case class Matched[A](
    rem: Input,
    trc: Trace,
    out: Task[Output[A]]
  ) extends EndpointResult[A] {
    def isMatched: Boolean = true
  }

  abstract class NotMatched extends EndpointResult[Nothing] {
    def isMatched: Boolean = false
  }

  object NotMatched extends NotMatched {
    final case class MethodNotAllowed(allowed: List[Method]) extends NotMatched
  }
}
