package io.finch.internal

import io.finch.FinchSpec

class HttpPathSpec extends FinchSpec {

  behavior of "HttpPath"

  it should "parse route correctly" in {
    check { p: Path =>
      p.p.route === p.segments && p.p.drop(1).route === p.segments
    }
  }
}
