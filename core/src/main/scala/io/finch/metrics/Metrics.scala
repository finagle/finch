package io.finch.metrics

trait Metrics {

  /**
    * Count a call to endpoint by path and method
    */
  def countCall(path: String): Unit

  /**
    * Count failure if exception was thrown during processing request to an endpoint
    */
  def countFailure(path: String, e: Throwable): Unit

  /**
    * Count time that was required to process failed request
    */
  def failureTime(path: String, e: Throwable, duration: Float): Unit

  /**
    * Count success if request was processed without exceptions
    */
  def countSuccess(path: String, code: Int): Unit

  /**
    * Count
    */
  def successTime(path: String, code: Int, duration: Float): Unit

  /**
    * Count size of response
    */
  def size(path: String, s: Float): Unit

}

object Metrics {

  val Null: Metrics = new Metrics {
    def countCall(path: String): Unit = ()

    def countFailure(path: String, e: Throwable): Unit = ()

    def failureTime(path: String, e: Throwable, duration: Float): Unit = ()

    def size(path: String, s: Float): Unit = ()

    def countSuccess(path: String, code: Int): Unit = ()

    def successTime(path: String, code: Int, duration: Float): Unit = ()
  }

}
