package io.finch.route

import org.scalatest.{Matchers, FlatSpec}
import org.scalatest.prop.Checkers
import shapeless.{::, HNil}

class SmashSpec extends FlatSpec with Matchers with Checkers {
  "Two non-HList values" should "be smashed appropriately" in {
    check { (s: String, i: Int) =>
      Smash.smash(s, i) === s :: i :: HNil
    }
  }

  "A non-HList value and an HList" should "be smashed appropriately" in {
    check { (s: String, i: Int, c: Char) =>
      Smash.smash(s, i :: c :: HNil) === s :: i :: c :: HNil
    }
  }

  "An HList and a non-HList value" should "be smashed appropriately" in {
    check { (s: String, i: Int, c: Char) =>
      Smash.smash(s :: i :: HNil, c) === s :: i :: c :: HNil
    }
  }

  "Two HLists" should "be smashed appropriately" in {
    check { (s: String, i: Int, c: Char) =>
      Smash.smash(s :: i :: HNil, c :: HNil) === s :: i :: c :: HNil &&
      Smash.smash(s :: HNil, i :: c :: HNil) === s :: i :: c :: HNil
    }
  }

  "An HNil and a non-empty HList" should "be smashed appropriately" in {
    check { (s: String, i: Int, c: Char) =>
      Smash.smash(s :: i :: c :: HNil, HNil) === s :: i :: c :: HNil
    }
  }

  "A non-empty HList and an HNil" should "be smashed appropriately" in {
    check { (s: String, i: Int, c: Char) =>
      Smash.smash(HNil, s :: i :: c :: HNil) === s :: i :: c :: HNil
    }
  }
}
