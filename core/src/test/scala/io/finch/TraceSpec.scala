package io.finch

class TraceSpec extends FinchSpec {

  behavior of "Trace"

  it should "round-trip concat/toList" in {
    check { l: List[String] =>
      val trace = l.foldLeft(Trace.empty)((t, s) => t.concat(Trace.segment(s)))
      trace.toList === l
    }
  }

  it should "concat two non-empty segments correctly" in {
    check { (a: Trace, b: Trace) =>
      a.concat(b).toList === a.toList ++ b.toList
    }
  }

  it should "create fromRoute" in {
    check { l: List[String] =>
      Trace.fromRoute(l).toList === l
    }
  }
}
