package io.finch

import java.util.UUID

import cats.Eval
import com.twitter.finagle.http.Method
import com.twitter.util.{Base64StringEncoder, Future}
import io.finch.internal.TooFastString
import shapeless._

/**
 * A collection of [[Endpoint]] combinators.
 */
trait Endpoints {

  private[this] val hnilFutureOutput: Eval[Future[Output[HNil]]] = Eval.now(Future.value(Output.payload(HNil)))

  type Endpoint0 = Endpoint[HNil]
  type Endpoint2[A, B] = Endpoint[A :: B :: HNil]
  type Endpoint3[A, B, C] = Endpoint[A :: B :: C :: HNil]

  /**
   * An universal [[Endpoint]] that matches the given string.
   */
  private[finch] class Matcher(s: String) extends Endpoint[HNil] {
    def apply(input: Input): Endpoint.Result[HNil] =
      input.headOption.flatMap {
        case `s` => Some((input.drop(1), hnilFutureOutput))
        case _ => None
      }

    override def toString = s
  }

  implicit def stringToMatcher(s: String): Endpoint0 = new Matcher(s)
  implicit def intToMatcher(i: Int): Endpoint0 = new Matcher(i.toString)
  implicit def booleanToMatcher(b: Boolean): Endpoint0 = new Matcher(b.toString)

  /**
   * An universal extractor that extracts some value of type `A` if it's possible to fetch the value from the string.
   */
  case class Extractor[A](name: String, f: String => Option[A]) extends Endpoint[A] {
    def apply(input: Input): Endpoint.Result[A] =
      for {
        ss <- input.headOption
        aa <- f(ss)
      } yield (input.drop(1), Eval.now(Future.value(Output.payload(aa))))

    def apply(n: String): Endpoint[A] = copy[A](name = n)

    override def toString: String = s":$name"
  }

  private[finch] case class StringExtractor(name: String) extends Endpoint[String] {
    def apply(input: Input): Endpoint.Result[String] =
      input.headOption.map(s => (input.drop(1), Eval.now(Future.value(Output.payload(s)))))

    def apply(n: String): Endpoint[String] = copy(name = n)

    override def toString: String = s":$name"
  }

  /**
   * An extractor that extracts a value of type `Seq[A]` from the tail of the route.
   */
  case class TailExtractor[A](name: String, f: String => Option[A]) extends Endpoint[Seq[A]] {
    def apply(input: Input): Endpoint.Result[Seq[A]] =
      Some((input.copy(path = Nil), Eval.now(Future.value(Output.payload(for {
        s <- input.path
        a <- f(s)
      } yield a)))))

    def apply(n: String): Endpoint[Seq[A]] = copy[A](name = n)

    override def toString: String = s":$name*"
  }

  private[this] def extractUUID(s: String): Option[UUID] =
    if (s.length != 36) None
    else try Some(UUID.fromString(s)) catch { case _: Exception => None }

  /**
   * An [[Endpoint]] that extract an integer value from the route.
   */
  object int extends Extractor("int", _.tooInt)

  /**
   * An [[Endpoint]] that extract an integer tail from the route.
   */
  object ints extends TailExtractor("int", _.tooInt)

  /**
   * An [[Endpoint]] that extract a long value from the route.
   */
  object long extends Extractor("long", _.tooLong)

  /**
   * An [[Endpoint]] that extract a long tail from the route.
   */
  object longs extends TailExtractor("long", _.tooLong)

  /**
   * An [[Endpoint]] that extract a string value from the route.
   */
  object string extends StringExtractor("string")

  /**
   * An [[Endpoint]] that extract a string tail from the route.
   */
  object strings extends TailExtractor("string", s => Some(s))

  /**
   * An [[Endpoint]] that extract a boolean value from the route.
   */
  object boolean extends Extractor("boolean", _.tooBoolean)

  /**
   * An [[Endpoint]] that extract a boolean tail from the route.
   */
  object booleans extends TailExtractor("boolean", _.tooBoolean)

  /**
   * An [[Endpoint]] that extract an UUID value from the route.
   */
  object uuid extends Extractor("uuid", extractUUID)

  /**
   * An [[Endpoint]] that extract an UUID tail from the route.
   */
  object uuids extends TailExtractor("uuid", extractUUID)

  /**
   * An [[Endpoint]] that skips all path parts.
   */
  object * extends Endpoint[HNil] {
    def apply(input: Input): Endpoint.Result[HNil] =
      Some((input.copy(path = Nil), hnilFutureOutput))

    override def toString: String = "*"
  }

  /**
   * An identity [[Endpoint]].
   */
  object / extends Endpoint[HNil] {
    def apply(input: Input): Endpoint.Result[HNil] =
      Some((input, hnilFutureOutput))

    override def toString: String = ""
  }

  private[this] def method[A](m: Method)(r: Endpoint[A]): Endpoint[A] = new Endpoint[A] {
    def apply(input: Input): Endpoint.Result[A] =
      if (input.request.method == m) r(input)
      else None

    override def toString: String = s"${m.toString().toUpperCase} /${r.toString}"
  }

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The resulting [[Endpoint]]
   * succeeds on the request only if its method is `GET` and the underlying router succeeds on it.
   */
  def get[A]: Endpoint[A] => Endpoint[A] = method(Method.Get)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The resulting [[Endpoint]]
   * succeeds on the request only if its method is `POST` and the underlying router succeeds on it.
   */
  def post[A]: Endpoint[A] => Endpoint[A] = method(Method.Post)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The resulting [[Endpoint]]
   * succeeds on the request only if its method is `PATCH` and the underlying router succeeds on it.
   */
  def patch[A]: Endpoint[A] => Endpoint[A] = method(Method.Patch)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The resulting [[Endpoint]]
   * succeeds on the request only if its method is `DELETE` and the underlying router succeeds on it.
   */
  def delete[A]: Endpoint[A] => Endpoint[A] = method(Method.Delete)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The resulting [[Endpoint]]
   * succeeds on the request only if its method is `HEAD` and the underlying router succeeds on it.
   */
  def head[A]: Endpoint[A] => Endpoint[A] = method(Method.Head)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The resulting [[Endpoint]]
   * succeeds on the request only if its method is `OPTIONS` and the underlying router succeeds on it.
   */
  def options[A]: Endpoint[A] => Endpoint[A] = method(Method.Options)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The resulting [[Endpoint]]
   * succeeds on the request only if its method is `PUT` and the underlying router succeeds on it.
   */
  def put[A]: Endpoint[A] => Endpoint[A] = method(Method.Put)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The resulting [[Endpoint]]
   * succeeds on the request only if its method is `CONNECT` and the underlying router succeeds on it.
   */
  def connect[A]: Endpoint[A] => Endpoint[A] = method(Method.Connect)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The resulting [[Endpoint]]
   * succeeds on the request only if its method is `TRACE` and the underlying router succeeds on it.
   */
  def trace[A]: Endpoint[A] => Endpoint[A] = method(Method.Trace)

  /**
   * An [[Exception]] representing a failed authorization with [[BasicAuth]].
   */
  object BasicAuthFailed extends Exception {
    override def getMessage: String = "Wrong credentials"
  }

  /**
   * Maintains Basic HTTP Auth for an arbitrary [[Endpoint]].
   */
  case class BasicAuth(user: String, password: String) {
    private[this] val userInfo = s"$user:$password"
    private[this] val expected = "Basic " + Base64StringEncoder.encode(userInfo.getBytes)

    def apply[A](e: Endpoint[A]): Endpoint[A] = new Endpoint[A] {
      private[this] val failedOutput: Eval[Future[Output[A]]] = Eval.now(Future.value(Unauthorized(BasicAuthFailed)))
      def apply(input: Input): Endpoint.Result[A] =
        input.request.authorization.flatMap {
          case `expected` => e(input)
          case _ => Some((input.copy(path = Seq.empty), failedOutput))
        }

      override def toString: String = s"BasicAuth($e)"
    }
  }
}
