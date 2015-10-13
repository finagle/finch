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
 * > sbt 'project benchmarks' 'run -prof gc io.finch.benchmarks.service.*Benchmark.*'
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(2)
abstract class UserServiceBenchmark(service: () => UserService)
  extends UserServiceApp(service)(8123, 2000, 10) {
  @Setup(Level.Invocation)
  def setUp(): Unit = setUpService()

  @TearDown(Level.Invocation)
  def tearDown(): Unit = tearDownService()

  @Benchmark
  def createUsers(): Seq[Response] = Await.result(runCreateUsers)

  @Benchmark
  def getUsers(): Seq[Response] = Await.result(runGetUsers)

  @Benchmark
  def updateUsers(): Seq[Response] = Await.result(runUpdateUsers)

  @Benchmark
  def getAllUsers(): Response = Await.result(runGetAllUsers)

  @Benchmark
  def deleteAllUsers(): Response = Await.result(runDeleteAllUsers)
}
