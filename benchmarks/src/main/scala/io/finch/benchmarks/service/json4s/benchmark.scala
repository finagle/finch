package io.finch.benchmarks.service
package json4s

import io.finch.json4s._

class Json4sBenchmark extends UserServiceBenchmark(new FinchUserService)

object Json4sBenchmark extends UserServiceApp(new FinchUserService) {
  def main(args: Array[String]): Unit = run()
}
