package io.finch

import java.util.UUID

import com.twitter.finagle.http._
import com.twitter.util.{Await, Future}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.Checkers
import org.scalatest.{Matchers, FlatSpec}

trait FinchSpec extends FlatSpec with Matchers with Checkers {

  case class Headers(m: Map[String, String])
  case class Cookies(c: Seq[Cookie])

  case class OptionalNonEmptyString(o: Option[String])

  implicit class OptionOutputOps[A](o: Option[(Input, () => Future[Output[A]])]) {
    def output: Option[Output[A]] = o.map({ case (_, oa) => Await.result(oa()) })
    def value: Option[A] = output.map(oa => oa.value)
    def remainder: Option[Input] = o.map(_._1)
  }

  def genNonEmptyString: Gen[String] = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)

  def genNonEmptyTuple: Gen[(String, String)] = for {
    key <- genNonEmptyString
    value <- genNonEmptyString
  } yield (key, value)

  def genHeaders: Gen[Headers] = Gen.mapOf(genNonEmptyTuple).map(Headers.apply)

  def genCookies: Gen[Cookies] = Gen.listOf(genNonEmptyTuple.map(t => new Cookie(t._1, t._2))).map(Cookies.apply)

  def genOptionalNonEmptyString: Gen[OptionalNonEmptyString] =
    Gen.option(genNonEmptyString).map(OptionalNonEmptyString.apply)

  def genStatus: Gen[Status] = Gen.oneOf(
    Status.Continue, Status.SwitchingProtocols, Status.Processing, Status.Ok, Status.Created, Status.Accepted,
    Status.NonAuthoritativeInformation, Status.NoContent, Status.ResetContent, Status.PartialContent,
    Status.MultiStatus, Status.MultipleChoices, Status.MovedPermanently, Status.Found, Status.SeeOther,
    Status.NotModified, Status.UseProxy, Status.TemporaryRedirect, Status.BadRequest, Status.Unauthorized,
    Status.PaymentRequired, Status.Forbidden, Status.NotFound, Status.MethodNotAllowed, Status.NotAcceptable,
    Status.ProxyAuthenticationRequired, Status.RequestTimeout, Status.Conflict, Status.Gone, Status.LengthRequired,
    Status.PreconditionFailed, Status.RequestEntityTooLarge, Status.RequestURITooLong,
    Status.UnsupportedMediaType, Status.RequestedRangeNotSatisfiable, Status.ExpectationFailed, Status.EnhanceYourCalm,
    Status.UnprocessableEntity, Status.Locked, Status.FailedDependency, Status.UnorderedCollection,
    Status.UpgradeRequired, Status.PreconditionRequired, Status.TooManyRequests, Status.RequestHeaderFieldsTooLarge,
    Status.ClientClosedRequest, Status.InternalServerError, Status.NotImplemented, Status.BadGateway,
    Status.ServiceUnavailable, Status.GatewayTimeout, Status.HttpVersionNotSupported, Status.VariantAlsoNegotiates,
    Status.InsufficientStorage, Status.NotExtended, Status.NetworkAuthenticationRequired
  )

  def genOutputContext: Gen[(Status, Map[String, String], List[Cookie], Option[String], Option[String])] = for {
    s <- genStatus
    hs <- genHeaders
    ct <- genOptionalNonEmptyString
    ch <- genOptionalNonEmptyString
  } yield (s, hs.m, List.empty[Cookie], ct.o, ch.o)

  def genFailureOutput: Gen[Output.Failure] = for {
    (s, hs, cs, ct, ch) <- genOutputContext
    m <- Arbitrary.arbitrary[Map[String, String]]
  } yield Output.Failure(m, s, hs.toMap, cs.toSeq, ct, ch)

  def genPayloadOutput[A: Arbitrary]: Gen[Output.Payload[A]] = for {
    (s, hs, cs, ct, ch) <- genOutputContext
    v <- Arbitrary.arbitrary[A]
  } yield Output.Payload(v, s, hs.toMap, cs.toSeq, ct, ch)

  def genOutput[A: Arbitrary]: Gen[Output[A]] = Gen.oneOf(
    genPayloadOutput[A],
    genFailureOutput.map(_.asInstanceOf[Output[A]])
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

  implicit def arbitraryRequest: Arbitrary[Request] = Arbitrary(
    for {
      m <- genMethod
      v <- genVersion
      s <- genPath
    } yield Request(v, m, s)
  )

  implicit def arbitraryInput: Arbitrary[Input] = Arbitrary(arbitraryRequest.arbitrary.map(Input.apply))

  implicit def arbitraryUUID: Arbitrary[UUID] = Arbitrary(Gen.uuid)

  implicit def arbitraryStatus: Arbitrary[Status] = Arbitrary(genStatus)

  implicit def arbitraryHeaders: Arbitrary[Headers] = Arbitrary(genHeaders)

  implicit def arbitraryCookies: Arbitrary[Cookies] = Arbitrary(genCookies)

  implicit def arbitraryOptionalNonEmptyString: Arbitrary[OptionalNonEmptyString] = Arbitrary(genOptionalNonEmptyString)

  implicit def arbitraryFailureOutput: Arbitrary[Output.Failure] = Arbitrary(genFailureOutput)

  implicit def arbitraryPayloadOutput[A: Arbitrary]: Arbitrary[Output.Payload[A]] = Arbitrary(genPayloadOutput[A])

  implicit def arbitraryOutput: Arbitrary[Output[Response]] =
    Arbitrary(genOutput[String].map(os => os.map(s => ToService.encodeResponse(s))))
}
