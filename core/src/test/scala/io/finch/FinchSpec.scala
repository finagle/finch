package io.finch

import com.twitter.finagle.http.{Request, Method, Version}
import com.twitter.util.{Await, Future}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.Checkers
import org.scalatest.{Matchers, FlatSpec}

trait FinchSpec extends FlatSpec with Matchers with Checkers {

  def genMethod: Gen[Method] = Gen.oneOf(
    Method.Get, Method.Connect, Method.Delete, Method.Head,
    Method.Options, Method.Patch, Method.Post, Method.Put, Method.Trace
  )

  def genVersion: Gen[Version] = Gen.oneOf(Version.Http10, Version.Http11)

  implicit def arbitraryRequest: Arbitrary[Request] = Arbitrary(
    for {
      m <- genMethod
      v <- genVersion
      n <- Gen.choose(0, 20)
      ss <- Gen.listOfN(n, Gen.oneOf(
        Gen.alphaStr.suchThat(_.nonEmpty),
        Gen.uuid.map(_.toString),
        Gen.posNum[Long].map(_.toString),
        Gen.oneOf(true, false).map(_.toString)
      ))
    } yield Request(v, m, "/" + ss.mkString("/"))
  )

  implicit def arbitraryInput: Arbitrary[Input] = Arbitrary(arbitraryRequest.arbitrary.map(Input.apply))

  def awaitOutput[A](o: Option[(Input, () => Future[Output[A]])]): Option[Output[A]] =
    o.map({ case (_, oa) => Await.result(oa()) })

  def awaitValue[A](o: Option[(Input, () => Future[Output[A]])]): Option[A] =
    awaitOutput(o).map(oa => oa.value)
}
