package io.finch.syntax.scala

import scala.concurrent.ExecutionContext

private[scala] object CallingThreadExecutionContext extends ExecutionContext {
  def execute(runnable: Runnable): Unit = runnable.run()

  def reportFailure(throwable: Throwable): Unit = throwable.printStackTrace()
}
