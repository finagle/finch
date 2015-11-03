package io.finch

import java.util.UUID

import com.twitter.finagle.http.{Request, Method, Version}
import com.twitter.io.Buf
import com.twitter.util.{Await, Future}
import io.finch.response.EncodeResponse
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.Checkers
import org.scalatest.{Matchers, FlatSpec}

trait FinchSpec extends FlatSpec with Matchers with Checkers {

  implicit val encodeMap: EncodeResponse[Map[String, String]] =
    EncodeResponse("application/json")(map =>
      Buf.Utf8(map.toSeq.map(kv => "\"" + kv._1 + "\":\"" + kv._2 + "\"").mkString(", "))
    )

  implicit class OptionOutputOps[A](o: Option[(Input, () => Future[Output[A]])]) {
    def output: Option[Output[A]] = o.map({ case (_, oa) => Await.result(oa()) })
    def value: Option[A] = output.map(oa => oa.value)
    def remainder: Option[Input] = o.map(_._1)
  }

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
}
