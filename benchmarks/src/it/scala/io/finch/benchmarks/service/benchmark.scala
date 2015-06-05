package io.finch.benchmarks.service

import io.finch.benchmarks.service.argonaut.ArgonautBenchmark
import io.finch.benchmarks.service.finagle.FinagleBenchmark
import io.finch.benchmarks.service.jackson.JacksonBenchmark
import io.finch.benchmarks.service.json4s.Json4sBenchmark
import org.scalatest.{FlatSpec, Matchers}

class BenchmarkTest extends FlatSpec with Matchers {
  "The Argonaut benchmark" should "run successfully" in {
    ArgonautBenchmark.main(Array.empty)
  }

  "The Finagle benchmark" should "run successfully" in {
    FinagleBenchmark.main(Array.empty)
  }

  "The Jackson benchmark" should "run successfully" in {
    JacksonBenchmark.main(Array.empty)
  }

  "The Json4s benchmark" should "run successfully" in {
    Json4sBenchmark.main(Array.empty)
  }
}
