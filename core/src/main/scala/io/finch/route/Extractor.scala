package io.finch.route

import com.twitter.util.{Future, Try}

/**
 * An universal extractor that extracts some value of type `A` if it's possible to fetch the value from the string.
 */
case class Extractor[A](name: String, f: String => A) extends Router[A] { self =>
  import Router._
  def apply(input: Input): Option[(Input, () => Future[A])] =
    for {
      ss <- input.headOption
      aa <- Try(f(ss)).toOption
    } yield (input.drop(1), () => Future.value(aa))

  def apply(n: String): Extractor[A] = copy[A](name = n)

  def * : Router[Seq[A]] = new Router[Seq[A]] {
    override def apply(input: Input): Option[(Input, () => Future[Seq[A]])] =
      Some((input.copy(path = Nil), () => Future.value(for {
        s <- input.path
        a <- Try(self.f(s)).toOption
      } yield a)))

    override def toString = self.toString + ".*"
  }

  override def toString: String = s":$name"
}

/**
 * A [[io.finch.route.Router Router]] that extract an integer from the route.
 */
object int extends Extractor("int", _.toInt)

/**
 * A [[io.finch.route.Router Router]] that extract a long value from the route.
 */
object long extends Extractor("long", _.toLong)

/**
 * A [[io.finch.route.Router Router]] that extract a string value from the route.
 */
object string extends Extractor("string", identity)

/**
 * A [[io.finch.route.Router Router]] that extract a boolean value from the route.
 */
object boolean extends Extractor("boolean", _.toBoolean)
