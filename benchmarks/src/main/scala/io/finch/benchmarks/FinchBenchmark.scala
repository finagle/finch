package io.finch.benchmarks

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 6, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 6, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(2)
abstract class FinchBenchmark
