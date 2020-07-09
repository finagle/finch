package io.finch.internal

import com.twitter.util.Try
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class TooFastStringSpec extends AnyFlatSpec with Matchers with Checkers {

  "TooFastString" should "parse boolean correctly" in {
    check { b: Boolean =>
      b.toString.tooBoolean === Some(b)
    }

    "".tooBoolean shouldBe None
    "foobarbaz".tooBoolean shouldBe None
  }

  it should "parse int correctly" in {
    check { i: Int =>
      i.toString.tooInt === Some(i)
    }

    check {
      forAll(Gen.numStr) { s =>
        Try(s.toInt).toOption === s.tooInt
      }
    }

    "".tooInt shouldBe None
    "9999999999".tooInt shouldBe None
    "foobarbaz".tooInt shouldBe None
    "-9876543210".tooInt shouldBe None
  }

  it should "parse long correctly" in {
    check { l: Long =>
      l.toString.tooLong === Some(l)
    }

    check {
      forAll(Gen.numStr) { s =>
        Try(s.toLong).toOption === s.tooLong
      }
    }

    "".tooLong shouldBe None
    "99999999999999999999".tooLong shouldBe None
    "foobarbazbarbazfoo".tooLong shouldBe None
    "-98765432101234567890".tooLong shouldBe None
  }
}
