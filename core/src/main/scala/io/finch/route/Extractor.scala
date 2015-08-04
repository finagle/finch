package io.finch.route

import com.twitter.util.{Future, Try}

/**
 * An universal extractor that extracts some value of type `A` if it's possible to fetch the value from the string.
 */
case class Extractor[A](name: String, f: String => A) extends Router[A] {
  import Router._
  def apply(input: Input): Option[(Input, () => Future[A])] =
    for {
      ss <- input.headOption
      aa <- Try(f(ss)).toOption
    } yield (input.drop(1), () => Future.value(aa))

  def apply(n: String): Router[A] = copy[A](name = n)

  override def toString: String = s":$name"
}

/**
 * An extractor that extracts a value of type `Seq[A]` from the tail of the route.
 */
case class TailExtractor[A](name: String, f: String => A) extends Router[Seq[A]] {
  import Router._
  def apply(input: Input): Option[(Input, () => Future[Seq[A]])] =
    Some((input.copy(path = Nil), () => Future.value(for {
      s <- input.path
      a <- Try(f(s)).toOption
    } yield a)))

  def apply(n: String): Router[Seq[A]] = copy[A](name = n)

  override def toString: String = s":$name*"
}

/**
 * A [[Router]] that extract an integer value from the route.
 */
object int extends Extractor("int", _.toInt)

/**
 * A [[Router]] that extract an integer tail from the route.
 */
object ints extends TailExtractor("int", _.toInt)

/**
 * A [[Router]] that extract a long value from the route.
 */
object long extends Extractor("long", _.toLong)

/**
 * A [[Router]] that extract a long tail from the route.
 */
object longs extends TailExtractor("long", _.toLong)

/**
 * A [[Router]] that extract a string value from the route.
 */
object string extends Extractor("string", identity)

/**
 * A [[Router]] that extract a string tail from the route.
 */
object strings extends TailExtractor("string", identity)

/**
 * A [[Router]] that extract a boolean value from the route.
 */
object boolean extends Extractor("boolean", _.toBoolean)

/**
 * A [[Router]] that extract a boolean tail from the route.
 */
object booleans extends TailExtractor("boolean", _.toBoolean)

