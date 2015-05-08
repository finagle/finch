package io.finch.benchmarks.service
package argonaut

import io.finch.argonaut._

class ArgonautBenchmark extends UserServiceBenchmark(new FinchUserService)

object ArgonautBenchmark extends UserServiceApp(new FinchUserService) {
  def main(args: Array[String]): Unit = run()
}
