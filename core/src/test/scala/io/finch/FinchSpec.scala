package io.finch

import java.nio.charset.{Charset, StandardCharsets}
import java.util.UUID

import cats.Eq
import cats.data.NonEmptyList
import cats.effect.{Effect, IO}
import cats.instances.AllInstances
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.http._
import com.twitter.io.Buf
import com.twitter.util._
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.Checkers
import org.typelevel.discipline.Laws
import scala.reflect.ClassTag
import shapeless.Witness

trait FinchSpec extends FlatSpec
  with Matchers
  with Checkers
  with AllInstances
  with MissingInstances
  with Endpoint.Module[IO] {

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

  // Structure for testing bug:
  // https://issues.scala-lang.org/browse/SI-2034
  object err1 {
    object err2 {
      case class Foo()
    }
  }

  def genError: Gen[Error] = for {
    i <- genRequestItem
    s <- genNonEmptyString
    e <- Gen.oneOf(
      Error.NotPresent(i),
      Error.NotParsed(i, implicitly[ClassTag[Int]], new Exception(s)),
      Error.NotParsed(i, implicitly[ClassTag[err1.err2.Foo]], new Exception(s)),
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

  def genOutputMeta: Gen[(Status, Option[Charset], Map[String, String], Seq[Cookie])] =
    genStatus.map(s => (s, Option.empty, Map.empty[String, String], Seq.empty[Cookie]))

  def genEmptyOutput: Gen[Output.Empty] = for {
    (st, cs, hs, c) <- genOutputMeta
  } yield Output.Empty(st, cs, hs, c)

  def genFailureOutput: Gen[Output.Failure] = for {
    (st, cs, hs, c) <- genOutputMeta
    s <- Gen.alphaStr
  } yield Output.Failure(new Exception(s), st, cs, hs, c)

  def genPayloadOutput[A: Arbitrary]: Gen[Output.Payload[A]] = for {
    (st, cs, hs, c) <- genOutputMeta
    a <- Arbitrary.arbitrary[A]
  } yield Output.Payload(a, st, cs, hs, c)

  def genAsyncStreamOutput[A: Arbitrary]: Gen[Output[AsyncStream[A]]] = Gen.oneOf(
    genEmptyOutput,
    for {
      (st, cs, hs, c) <- genOutputMeta
      a <- Arbitrary.arbitrary[A]
    } yield Output.Payload(AsyncStream.of(a), st, cs, hs, c)
  )

  def genOutput[A: Arbitrary]: Gen[Output[A]] = Gen.oneOf(
    genPayloadOutput[A], genFailureOutput, genEmptyOutput
  )

  def genAccept: Gen[Accept] = {
    def witness[T <: String](implicit w: Witness.Aux[T]): String = w.value
    Gen.oneOf(
      witness[Application.Json],
      witness[Application.AtomXml],
      witness[Application.Csv],
      witness[Application.Javascript],
      witness[Application.OctetStream],
      witness[Application.RssXml],
      witness[Application.AtomXml],
      witness[Application.WwwFormUrlencoded],
      witness[Application.Xml],
      witness[Text.Plain],
      witness[Text.Html],
      witness[Text.EventStream]
    ).map(s => Accept.fromString(s))
  }

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
    size <- Gen.choose(1, 100)
    bytes <- Gen.listOfN(size, Arbitrary.arbByte.arbitrary)
    begin <- Gen.choose(0, size)
    end <- Gen.choose(begin, size)
  } yield Buf.ByteArray.Owned(bytes.toArray, begin, end)

  def genTrace: Gen[Trace] = Gen.oneOf(
    Gen.const(Trace.empty),
    Gen.nonEmptyListOf(Gen.alphaStr).map(l =>
      l.foldLeft(Trace.empty)((t, s) => t.concat(Trace.segment(s)))
    )
  )

  implicit def arbitraryRequest: Arbitrary[Request] = Arbitrary(
    for {
      m <- genMethod
      v <- genVersion
      s <- genPath
      b <- genBuf
      a <- genAccept
    } yield {
      val r = Request(v, m, s.p)
      r.content = b
      r.contentLength = b.length.toLong
      r.charset = "utf-8"
      r.accept = s"${a.primary}/${a.sub}"
      r
    }
  )

  implicit def arbitraryAccept: Arbitrary[Accept] = Arbitrary(genAccept)

  implicit def cogenRequest: Cogen[Request] =
    Cogen[(String, String, String, Array[Byte])].contramap { r =>
      (r.method.toString, r.version.toString, r.path, Buf.ByteArray.Owned.extract(r.content))
    }

  implicit def arbitraryEndpoint[F[_] : Effect, A](implicit A: Arbitrary[A]): Arbitrary[Endpoint[F, A]] = Arbitrary(
    Gen.oneOf(
      Gen.const(Endpoint[F].empty[A]),
      A.arbitrary.map(a => Endpoint[F].const(a)),
      Arbitrary.arbitrary[Throwable].map(e =>
        Endpoint[F].liftOutputAsync(Effect[F].raiseError[Output[A]](e))
      ),
      /**
       * Note that we don't provide instances of arbitrary endpoints wrapping
       * `Input => Output[A]` since `Endpoint` isn't actually lawful in this
       * respect.
       */
      Arbitrary.arbitrary[Input => A].map { f =>
        new Endpoint[F, A] {
          final def apply(input: Input): Endpoint.Result[F, A] =
            EndpointResult.Matched(input, Trace.empty, Effect[F].delay(Output.payload(f(input))))
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
  implicit def eqEndpoint[F[_] : Effect, A: Eq]: Eq[Endpoint[F, A]] = new Eq[Endpoint[F, A]] {
    private[this] def count: Int = 16

    private[this] def await(result: Endpoint.Result[F, A]): Option[(Input, Try[Output[A]])] = for {
      r <- result.remainder
      o <- result.awaitOutput()
    } yield (r, o)

    private[this] def inputs: Stream[Input] = Stream.continually(
      Arbitrary.arbitrary[Input].sample
    ).flatten

    override def eqv(x: Endpoint[F, A], y: Endpoint[F, A]): Boolean = inputs.take(count).forall { input =>
      val resultX = await(x(input))
      val resultY = await(y(input))

      Eq[Option[(Input, Try[Output[A]])]].eqv(resultX, resultY)
    }
  }

  implicit def arbitraryInput: Arbitrary[Input] =
    Arbitrary(arbitraryRequest.arbitrary.map(Input.fromRequest))

  implicit def cogenInput: Cogen[Input] =
    Cogen[(Request, Seq[String])].contramap(input => (input.request, input.route))

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

  implicit def arbitraryAsyncStreamOutput[A: Arbitrary]: Arbitrary[Output[AsyncStream[A]]] =
    Arbitrary(genAsyncStreamOutput[A])

  implicit def arbitraryRequestItem: Arbitrary[items.RequestItem] =
    Arbitrary(genRequestItem)

  implicit def arbitraryError: Arbitrary[Error] =
    Arbitrary(genError)

  implicit def arbitraryErrors: Arbitrary[Errors] =
    Arbitrary(genErrors)

  implicit def arbitraryTrace: Arbitrary[Trace] = Arbitrary(genTrace)
}
