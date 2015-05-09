package io.finch.benchmarks.service
package jackson

import io.finch.jackson._

class JacksonBenchmark extends UserServiceBenchmark(new FinchUserService)

object JacksonBenchmark extends UserServiceApp(new FinchUserService) {
  def main(args: Array[String]): Unit = run()
}
