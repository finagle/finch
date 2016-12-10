package io.finch

import java.nio.charset.{Charset, StandardCharsets}
import java.util.UUID

import cats.Eq
import cats.data.NonEmptyList
import cats.instances.AllInstances
import com.twitter.finagle.http._
import com.twitter.io.Buf
import com.twitter.util.{Await, Future, Try}
import io.catbird.util.Rerunnable
import io.finch.Endpoint.Result
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.Checkers
import org.typelevel.discipline.Laws
import scala.reflect.ClassTag

trait FinchSpec extends FlatSpec with Matchers with Checkers with AllInstances
  with MissingInstances {

  def checkAll(name: String, ruleSet: Laws#RuleSet): Unit = {
    for ((id, prop) <- ruleSet.all.properties)
      it should (name + "." + id) in {
        check(prop)
      }
  }

  case class BasicAuthCredentials(user: String, pass: String)

  case class Headers(m: Map[String, String])
  case class Params(p: Map[String, String])
  case class Cookies(c: Seq[Cookie])
  case class Path(p: String)

  case class OptionalNonEmptyString(o: Option[String])

  def genBasicAuthCredentials: Gen[BasicAuthCredentials] = for {
    u <- Gen.alphaStr.suchThat(!_.contains(':'))
    p <- Gen.alphaStr
  } yield BasicAuthCredentials(u, p)

  def genNonEmptyString: Gen[String] = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)

  def genRequestItem: Gen[items.RequestItem] = for {
    s <- genNonEmptyString
    i <- Gen.oneOf(
      items.BodyItem, items.ParamItem(s), items.HeaderItem(s),
      items.MultipleItems, items.CookieItem(s)
    )
  } yield i

  def genError: Gen[Error] = for {
    i <- genRequestItem
    s <- genNonEmptyString
    e <- Gen.oneOf(
      Error.NotPresent(i),
      Error.NotParsed(i, implicitly[ClassTag[Int]], new Exception(s)),
      Error.NotValid(i, s)
    )
  } yield e

  def genErrors: Gen[Errors] =
    Gen.nonEmptyListOf(genError).map(l => Errors(NonEmptyList.fromListUnsafe(l)))

  def genNonEmptyTuple: Gen[(String, String)] = for {
    key <- genNonEmptyString
    value <- genNonEmptyString
  } yield (key, value)

  def genHeaders: Gen[Headers] = Gen.mapOf(genNonEmptyTuple).map(m =>
    Headers(m.map(kv => kv._1.toLowerCase -> kv._2.toLowerCase))
  )

  def genParams: Gen[Params] = Gen.mapOf(genNonEmptyTuple).map(m =>
    Params(m.map(kv => kv._1.toLowerCase -> kv._2.toLowerCase))
  )

  def genCookies: Gen[Cookies] =
    Gen.listOf(genNonEmptyTuple.map(t => new Cookie(t._1, t._2))).map(Cookies.apply)

  def genOptionalNonEmptyString: Gen[OptionalNonEmptyString] =
    Gen.option(genNonEmptyString).map(OptionalNonEmptyString.apply)

  def genStatus: Gen[Status] = Gen.oneOf(
    Status.Continue, Status.SwitchingProtocols, Status.Processing, Status.Ok, Status.Created,
    Status.Accepted, Status.NonAuthoritativeInformation, Status.NoContent, Status.ResetContent,
    Status.PartialContent, Status.MultiStatus, Status.MultipleChoices, Status.MovedPermanently,
    Status.Found, Status.SeeOther, Status.NotModified, Status.UseProxy, Status.TemporaryRedirect,
    Status.BadRequest, Status.Unauthorized, Status.PaymentRequired, Status.Forbidden,
    Status.NotFound, Status.MethodNotAllowed, Status.NotAcceptable,
    Status.ProxyAuthenticationRequired, Status.RequestTimeout, Status.Conflict, Status.Gone,
    Status.LengthRequired, Status.PreconditionFailed, Status.RequestEntityTooLarge,
    Status.RequestURITooLong, Status.UnsupportedMediaType, Status.RequestedRangeNotSatisfiable,
    Status.ExpectationFailed, Status.EnhanceYourCalm, Status.UnprocessableEntity, Status.Locked,
    Status.FailedDependency, Status.UnorderedCollection, Status.UpgradeRequired,
    Status.PreconditionRequired, Status.TooManyRequests, Status.RequestHeaderFieldsTooLarge,
    Status.ClientClosedRequest, Status.InternalServerError, Status.NotImplemented,
    Status.BadGateway, Status.ServiceUnavailable, Status.GatewayTimeout,
    Status.HttpVersionNotSupported, Status.VariantAlsoNegotiates, Status.InsufficientStorage,
    Status.NotExtended, Status.NetworkAuthenticationRequired
  )

  def genCharset: Gen[Charset] = Gen.oneOf(
    StandardCharsets.ISO_8859_1, StandardCharsets.US_ASCII, StandardCharsets.UTF_8,
    StandardCharsets.UTF_16, StandardCharsets.UTF_16BE, StandardCharsets.UTF_16LE
  )

  def genOutputMeta: Gen[Output.Meta] =
    genStatus.map(s => Output.Meta(s, Option.empty, Map.empty[String, String], Seq.empty[Cookie]))

  def genEmptyOutput: Gen[Output.Empty] = for {
    m <- genOutputMeta
  } yield Output.Empty(m)

  def genFailureOutput: Gen[Output.Failure] = for {
    m <- genOutputMeta
    s <- Gen.alphaStr
  } yield Output.Failure(new Exception(s), m)

  def genPayloadOutput[A: Arbitrary]: Gen[Output.Payload[A]] = for {
    m <- genOutputMeta
    a <- Arbitrary.arbitrary[A]
  } yield Output.Payload(a, m)

  def genOutput[A: Arbitrary]: Gen[Output[A]] = Gen.oneOf(
    genPayloadOutput[A], genFailureOutput, genEmptyOutput
  )

  def genMethod: Gen[Method] = Gen.oneOf(
    Method.Get, Method.Connect, Method.Delete, Method.Head,
    Method.Options, Method.Patch, Method.Post, Method.Put, Method.Trace
  )

  def genVersion: Gen[Version] = Gen.oneOf(Version.Http10, Version.Http11)

  def genPath: Gen[Path] = for {
    n <- Gen.choose(0, 20)
    ss <- Gen.listOfN(n, Gen.oneOf(
      Gen.alphaStr.suchThat(_.nonEmpty),
      Gen.uuid.map(_.toString),
      Gen.posNum[Long].map(_.toString),
      Gen.oneOf(true, false).map(_.toString)
    ))
  } yield Path("/" + ss.mkString("/"))

  def genBuf: Gen[Buf] = for {
    s <- Arbitrary.arbitrary[String]
    b <- Gen.oneOf(
      Buf.Utf8(s),
      Buf.ByteArray.Owned(s.getBytes("UTF-8"))
    )
  } yield b

  implicit def arbitraryRequest: Arbitrary[Request] = Arbitrary(
    for {
      m <- genMethod
      v <- genVersion
      s <- genPath
      b <- genBuf
    } yield {
      val r = Request(v, m, s.p)
      r.content = b
      r.contentLength = b.length.toLong
      r.charset = "utf-8"
      r
    }
  )

  implicit def cogenRequest: Cogen[Request] =
    Cogen[(String, String, String, Array[Byte])].contramap { r =>
      (r.method.toString, r.version.toString, r.path, Buf.ByteArray.Owned.extract(r.content))
    }

  implicit def arbitraryEndpoint[A](implicit A: Arbitrary[A]): Arbitrary[Endpoint[A]] = Arbitrary(
    Gen.oneOf(
      Gen.const(Endpoint.empty[A]),
      A.arbitrary.map(a => Endpoint.const(a)),
      Arbitrary.arbitrary[Throwable].map { error =>
        new Endpoint[A] {
          override def apply(input: Input): Result[A] =
            Some(input -> Rerunnable.fromFuture(Future.exception(error)))
        }
      },
      /**
       * Note that we don't provide instances of arbitrary endpoints wrapping
       * `Input => Output[A]` since `Endpoint` isn't actually lawful in this
       * respect.
       */
      Arbitrary.arbitrary[Input => A].map { f =>
        new Endpoint[A] {
          override def apply(input: Input): Result[A] =
            Some(input -> Rerunnable.fromFuture(Future.value(Ok(f(input)))))
        }
      }
    )
  )

  /**
   * Equality instance for [[io.finch.Endpoint]].
   *
   * We attempt to verify that two endpoints are the same by applying them to a
   * fixed number of randomly generated inputs.
   */
  implicit def eqEndpoint[A: Eq]: Eq[Endpoint[A]] = new Eq[Endpoint[A]] {
    private[this] def count: Int = 16

    private[this] def await(result: Endpoint.Result[A]): Option[(Input, Try[Output[A]])] =
      result.map {
        case (input, rerun) => (input, Await.result(rerun.liftToTry.run))
      }

    private[this] def inputs: Stream[Input] = Stream.continually(
      Arbitrary.arbitrary[Input].sample
    ).flatten

    override def eqv(x: Endpoint[A], y: Endpoint[A]): Boolean = inputs.take(count).forall { input =>
      val resultX = await(x(input))
      val resultY = await(y(input))

      Eq[Option[(Input, Try[Output[A]])]].eqv(resultX, resultY)
    }
  }

  implicit def arbitraryInput: Arbitrary[Input] =
    Arbitrary(arbitraryRequest.arbitrary.map(Input.request))

  implicit def cogenInput: Cogen[Input] =
    Cogen[(Request, Seq[String])].contramap(input => (input.request, input.path))

  implicit def arbitraryUUID: Arbitrary[UUID] = Arbitrary(Gen.uuid)

  implicit def arbitraryStatus: Arbitrary[Status] = Arbitrary(genStatus)

  implicit def arbitraryBasicAuthCredentials: Arbitrary[BasicAuthCredentials] = Arbitrary(genBasicAuthCredentials)

  implicit def arbitraryHeaders: Arbitrary[Headers] = Arbitrary(genHeaders)

  implicit def arbitraryCookies: Arbitrary[Cookies] = Arbitrary(genCookies)

  implicit def arbitraryParams: Arbitrary[Params] = Arbitrary(genParams)

  implicit def arbitraryPath: Arbitrary[Path] = Arbitrary(genPath)

  implicit def arbitraryCharset: Arbitrary[Charset] = Arbitrary(genCharset)

  implicit def arbitraryBuf: Arbitrary[Buf] = Arbitrary(genBuf)

  implicit def arbitraryOptionalNonEmptyString: Arbitrary[OptionalNonEmptyString] =
    Arbitrary(genOptionalNonEmptyString)

  implicit def arbitraryFailureOutput: Arbitrary[Output.Failure] = Arbitrary(genFailureOutput)

  implicit def arbitraryEmptyOutput: Arbitrary[Output.Empty] = Arbitrary(genEmptyOutput)

  implicit def arbitraryPayloadOutput[A: Arbitrary]: Arbitrary[Output.Payload[A]] =
    Arbitrary(genPayloadOutput[A])

  implicit def arbitraryOutput[A: Arbitrary]: Arbitrary[Output[A]] = Arbitrary(genOutput[A])

  implicit def arbitraryRequestItem: Arbitrary[items.RequestItem] =
    Arbitrary(genRequestItem)

  implicit def arbitraryError: Arbitrary[Error] =
    Arbitrary(genError)

  implicit def arbitraryErrors: Arbitrary[Errors] =
    Arbitrary(genErrors)
}
