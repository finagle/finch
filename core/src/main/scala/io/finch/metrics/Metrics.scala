package io.finch.metrics

trait Metrics {

  /**
    * Count call to endpoint by the path and method
    */
  def countCall(path: String): Unit

  /**
    * Count failure if exception was thrown during processing a request to an endpoint
    */
  def countFailure(path: String, e: Throwable): Unit

  /**
    * Count time that was required to process a failed request
    */
  def failureTime(path: String, e: Throwable, duration: Float): Unit

  /**
    * Count success if a request was processed without exceptions
    */
  def countSuccess(path: String, code: Int): Unit

  /**
    * Count time that was required to process a successful request
    */
  def successTime(path: String, code: Int, duration: Float): Unit

  /**
    * Count size of a response
    */
  def size(path: String, code: Int, s: Float): Unit

}

object Metrics {

  val Null: Metrics = new Metrics {
    def countCall(path: String): Unit = ()

    def countFailure(path: String, e: Throwable): Unit = ()

    def failureTime(path: String, e: Throwable, duration: Float): Unit = ()

    def size(path: String, code: Int, s: Float): Unit = ()

    def countSuccess(path: String, code: Int): Unit = ()

    def successTime(path: String, code: Int, duration: Float): Unit = ()
  }

}
