package io.finch

import com.twitter.finagle.http.Response
import shapeless.ops.function.FnToProduct
import scala.annotation.targetName
import cats.MonadError
import cats.syntax.all._

/**
 * Enables a very simple syntax allowing to "map" endpoints to arbitrary functions. The types are resolved at compile time and no reflection is used.
 *
 * For example:
 *
 * {{{
 *   import io.finch._
 *   import io.cats.effect.IO
 *
 *   object Mapping extends Endpoint.Module[IO] {
 *     def hello = get("hello" :: path[String]) { s: String =>
 *       Ok(s)
 *     }
 *   }
 * }}}
 */
trait Mappable[F[_], A] extends Endpoint[F, A] {
  self =>

  @targetName("mapOutputApply")
  final def apply[B](f: A => Output[B])(using MonadError[F, Throwable]): Endpoint[F, B] = self.mapOutput(f)

  @targetName("mapHKOutputApply")
  final def apply[B](f: A => F[Output[B]])(using MonadError[F, Throwable]): Endpoint[F, B] = self.mapOutputAsync(f)

  @targetName("mapResponseApply")
  final def apply(f: A => Response)(using MonadError[F, Throwable]): Endpoint[F, Response] =
    self.mapOutput(f.andThen(r => Output.payload(r, r.status)))

  @targetName("mapHKResponseApply")
  final def apply(f: A => F[Response])(using MonadError[F, Throwable]): Endpoint[F, Response] =
    self.mapOutputAsync(f.andThen(ff => ff.map(r => Output.payload(r, r.status))))

  @targetName("outputApply")
  final def apply[B](o: => Output[B])(using F: MonadError[F, Throwable]): Endpoint[F, B] = self.mapOutput(_ => o)

  @targetName("responseApply")
  final def apply(r: => Response)(using F: MonadError[F, Throwable]): Endpoint[F, Response] = self.mapOutput(_ => Output.payload(r, r.status))

  @targetName("hkOutputApply")
  final def apply[B](o: => F[Output[B]])(using F: MonadError[F, Throwable]): Endpoint[F, B] = self.mapOutputAsync(_ => o)

  @targetName("hkResponseApply")
  final def apply(r: => F[Response])(using F: MonadError[F, Throwable]): Endpoint[F, Response] =
    self.mapOutputAsync(_ => r.map(res => Output.payload(res, res.status)))

}

object Mappable {

  extension [F[_], A, B](self: Mappable[F, A *: B *: EmptyTuple]) {
    @targetName("mapFn2OutputApply")
    def apply[C](f: (A, B) => Output[C])(using MonadError[F, Throwable]): Endpoint[F, C] = self.mapOutput(t => f.tupled(t))

    @targetName("mapFn2ResponseApply")
    def apply(f: (A, B) => Response)(using MonadError[F, Throwable]): Endpoint[F, Response] = self.mapOutput { t =>
      val r = f.tupled(t)
      Output.payload(r, r.status)
    }

    @targetName("mapFn2HKOutputApply")
    def apply[C](f: (A, B) => F[Output[C]])(using MonadError[F, Throwable]): Endpoint[F, C] = self.mapOutputAsync(t => f.tupled(t))

    @targetName("mapFn2HKResponseApply")
    def apply(f: (A, B) => F[Response])(using MonadError[F, Throwable]): Endpoint[F, Response] =
      self.mapOutputAsync(t => f.tupled(t).map(r => Output.payload(r, r.status)))
  }

  extension [F[_], A, B, C](self: Mappable[F, A *: B *: C *: EmptyTuple]) {
    @targetName("mapFn3OutputApply")
    def apply[D](f: (A, B, C) => Output[D])(using MonadError[F, Throwable]): Endpoint[F, D] = self.mapOutput(t => f.tupled(t))

    @targetName("mapFn3ResponseApply")
    def apply(f: (A, B, C) => Response)(using MonadError[F, Throwable]): Endpoint[F, Response] = self.mapOutput { t =>
      val r = f.tupled(t)
      Output.payload(r, r.status)
    }

    @targetName("mapFn3HKOutputApply")
    def apply[D](f: (A, B, C) => F[Output[D]])(using MonadError[F, Throwable]): Endpoint[F, D] = self.mapOutputAsync(t => f.tupled(t))

    @targetName("mapFn3HKResponseApply")
    def apply(f: (A, B, C) => F[Response])(using MonadError[F, Throwable]): Endpoint[F, Response] =
      self.mapOutputAsync(t => f.tupled(t).map(r => Output.payload(r, r.status)))

  }

  extension [F[_], A, B, C, D](self: Mappable[F, A *: B *: C *: D *: EmptyTuple]) {
    @targetName("mapFn4OutputApply")
    def apply[E](f: (A, B, C, D) => Output[E])(using MonadError[F, Throwable]): Endpoint[F, E] = self.mapOutput(t => f.tupled(t))

    @targetName("mapFn4ResponseApply")
    def apply(f: (A, B, C, D) => Response)(using MonadError[F, Throwable]): Endpoint[F, Response] = self.mapOutput { t =>
      val r = f.tupled(t)
      Output.payload(r, r.status)
    }

    @targetName("mapFn4HKOutputApply")
    def apply[E](f: (A, B, C, D) => F[Output[E]])(using MonadError[F, Throwable]): Endpoint[F, E] = self.mapOutputAsync(t => f.tupled(t))

    @targetName("mapFn4HKResponseApply")
    def apply(f: (A, B, C, D) => F[Response])(using MonadError[F, Throwable]): Endpoint[F, Response] =
      self.mapOutputAsync(t => f.tupled(t).map(r => Output.payload(r, r.status)))

  }

  extension [F[_], A, B, C, D, E](self: Mappable[F, A *: B *: C *: D *: E *: EmptyTuple]) {
    @targetName("mapFn5OutputApply")
    def apply[G](f: (A, B, C, D, E) => Output[G])(using MonadError[F, Throwable]): Endpoint[F, G] = self.mapOutput(t => f.tupled(t))

    @targetName("mapFn5ResponseApply")
    def apply(f: (A, B, C, D, E) => Response)(using MonadError[F, Throwable]): Endpoint[F, Response] = self.mapOutput { t =>
      val r = f.tupled(t)
      Output.payload(r, r.status)
    }

    @targetName("mapFn5HKOutputApply")
    def apply[G](f: (A, B, C, D, E) => F[Output[G]])(using MonadError[F, Throwable]): Endpoint[F, G] = self.mapOutputAsync(t => f.tupled(t))

    @targetName("mapFn5HKResponseApply")
    def apply(f: (A, B, C, D, E) => F[Response])(using MonadError[F, Throwable]): Endpoint[F, Response] =
      self.mapOutputAsync(t => f.tupled(t).map(r => Output.payload(r, r.status)))

  }

  extension [F[_], A, B, C, D, E, G](self: Mappable[F, A *: B *: C *: D *: E *: G *: EmptyTuple]) {
    @targetName("mapFn6OutputApply")
    def apply[H](f: (A, B, C, D, E, G) => Output[H])(using MonadError[F, Throwable]): Endpoint[F, H] = self.mapOutput(t => f.tupled(t))

    @targetName("mapFn6ResponseApply")
    def apply(f: (A, B, C, D, E, G) => Response)(using MonadError[F, Throwable]): Endpoint[F, Response] = self.mapOutput { t =>
      val r = f.tupled(t)
      Output.payload(r, r.status)
    }

    @targetName("mapFn6HKOutputApply")
    def apply[H](f: (A, B, C, D, E, G) => F[Output[H]])(using MonadError[F, Throwable]): Endpoint[F, H] = self.mapOutputAsync(t => f.tupled(t))

    @targetName("mapFn6HKResponseApply")
    def apply(f: (A, B, C, D, E, G) => F[Response])(using MonadError[F, Throwable]): Endpoint[F, Response] =
      self.mapOutputAsync(t => f.tupled(t).map(r => Output.payload(r, r.status)))

  }

  extension [F[_], A, B, C, D, E, G, H](self: Mappable[F, A *: B *: C *: D *: E *: G *: H *: EmptyTuple]) {
    @targetName("mapFn7OutputApply")
    def apply[K](f: (A, B, C, D, E, G, H) => Output[K])(using MonadError[F, Throwable]): Endpoint[F, K] = self.mapOutput(t => f.tupled(t))

    @targetName("mapFn7ResponseApply")
    def apply(f: (A, B, C, D, E, G, H) => Response)(using MonadError[F, Throwable]): Endpoint[F, Response] = self.mapOutput { t =>
      val r = f.tupled(t)
      Output.payload(r, r.status)
    }

    @targetName("mapFn7HKOutputApply")
    def apply[K](f: (A, B, C, D, E, G, H) => F[Output[K]])(using MonadError[F, Throwable]): Endpoint[F, K] = self.mapOutputAsync(t => f.tupled(t))

    @targetName("mapFn7HKResponseApply")
    def apply(f: (A, B, C, D, E, G, H) => F[Response])(using MonadError[F, Throwable]): Endpoint[F, Response] =
      self.mapOutputAsync(t => f.tupled(t).map(r => Output.payload(r, r.status)))

  }

}
