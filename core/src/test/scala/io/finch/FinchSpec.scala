package io.finch

import java.util.UUID

import cats.std.AllInstances
import com.twitter.finagle.http._
import com.twitter.io.Buf
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.Checkers
import org.scalatest.{Matchers, FlatSpec}
import org.typelevel.discipline.Laws

trait FinchSpec extends FlatSpec with Matchers with Checkers with AllInstances
  with MissingInstances {

  def checkAll(name: String, ruleSet: Laws#RuleSet): Unit = {
    for ((id, prop) <- ruleSet.all.properties)
      it should (name + "." + id) in {
        check(prop)
      }
  }

  case class Headers(m: Map[String, String])
  case class Params(p: Map[String, String])
  case class Cookies(c: Seq[Cookie])

  case class OptionalNonEmptyString(o: Option[String])

  def genNonEmptyString: Gen[String] = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)

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

  def genOutputMeta: Gen[Output.Meta] =
    genStatus.map(s => Output.Meta(s, Map.empty[String, String], Seq.empty[Cookie]))

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

  def genPath: Gen[String] = for {
    n <- Gen.choose(0, 20)
    ss <- Gen.listOfN(n, Gen.oneOf(
      Gen.alphaStr.suchThat(_.nonEmpty),
      Gen.uuid.map(_.toString),
      Gen.posNum[Long].map(_.toString),
      Gen.oneOf(true, false).map(_.toString)
    ))
  } yield "/" + ss.mkString("/")

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
      val r = Request(v, m, s)
      r.content = b
      r.contentLength = b.length.toLong
      r.charset = "utf-8"
      r
    }
  )

  implicit def arbitraryInput: Arbitrary[Input] =
    Arbitrary(arbitraryRequest.arbitrary.map(Input.apply))

  implicit def arbitraryUUID: Arbitrary[UUID] = Arbitrary(Gen.uuid)

  implicit def arbitraryStatus: Arbitrary[Status] = Arbitrary(genStatus)

  implicit def arbitraryHeaders: Arbitrary[Headers] = Arbitrary(genHeaders)

  implicit def arbitraryCookies: Arbitrary[Cookies] = Arbitrary(genCookies)

  implicit def arbitraryParams: Arbitrary[Params] = Arbitrary(genParams)

  implicit def arbitraryOptionalNonEmptyString: Arbitrary[OptionalNonEmptyString] =
    Arbitrary(genOptionalNonEmptyString)

  implicit def arbitraryFailureOutput: Arbitrary[Output.Failure] = Arbitrary(genFailureOutput)

  implicit def arbitraryEmptyOutput: Arbitrary[Output.Empty] = Arbitrary(genEmptyOutput)

  implicit def arbitraryPayloadOutput[A: Arbitrary]: Arbitrary[Output.Payload[A]] =
    Arbitrary(genPayloadOutput[A])

  implicit def arbitraryOutput[A: Arbitrary]: Arbitrary[Output[A]] = Arbitrary(genOutput[A])
}
