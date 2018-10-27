package io.finch.internal

import scala.concurrent.ExecutionContext

object DummyExecutionContext extends ExecutionContext {
  def execute(runnable: Runnable): Unit = runnable.run()
  def reportFailure(cause: Throwable): Unit = throw new NotImplementedError()
}
