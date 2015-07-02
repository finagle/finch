package io.finch

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}

package object petstore {
  implicit val statusArbitrary: Arbitrary[Status] =
    Arbitrary(Gen.oneOf(Available, Pending, Adopted))

  implicit val categoryArbitrary: Arbitrary[Category] = Arbitrary(
    for {
      id <- arbitrary[Long]
      name <- Gen.alphaStr
    } yield Category(id, name)
  )

  implicit val tagArbitrary: Arbitrary[Tag] = Arbitrary(
    for {
      id <- arbitrary[Long]
      name <- Gen.alphaStr
    } yield Tag(id, name)
  )

  implicit val petArbitrary: Arbitrary[Pet] = Arbitrary(
    for {
      id <- arbitrary[Option[Long]]
      name <- arbitrary[String] suchThat (s=> s != null && s.nonEmpty)
      photoUrls <- arbitrary[Seq[String]]
      category <- arbitrary[Category]
      tags <- arbitrary[Seq[Tag]]
      status <- arbitrary[Status]
    } yield Pet(id, name, photoUrls, Option(category), Option(tags), Option(status))
  )
}