package io.finch.benchmarks.service

import com.twitter.finagle.httpx.Response
import com.twitter.util.Await
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

/**
 * A benchmark for a simple user service, designed to help with comparing the
 * performance of Finch and the finagle-http API and the relative performance of
 * the different JSON libraries supported by Finch.
 *
 * The following command will run all user service benchmarks with reasonable
 * settings:
 *
 * > sbt "benchmarks/run -i 10 -wi 10 -f 2 -t 1 io.finch.benchmarks.service.*"
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
abstract class UserServiceBenchmark(service: () => UserService)
  extends UserServiceApp(service)(8123, 2000, 10) {
  @Setup(Level.Invocation)
  def setUp(): Unit = setUpService()

  @TearDown(Level.Invocation)
  def tearDown(): Unit = tearDownService()

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  def createUsers(): Seq[Response] = Await.result(runCreateUsers)

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  def getUsers(): Seq[Response] = Await.result(runGetUsers)

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  def updateUsers(): Seq[Response] = Await.result(runUpdateUsers)

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  def getAllUsers(): Response = Await.result(runGetAllUsers)

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  def deleteAllUsers(): Response = Await.result(runDeleteAllUsers)
}
