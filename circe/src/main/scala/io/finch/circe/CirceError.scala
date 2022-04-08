package io.finch.circe

import cats.syntax.show._

import scala.util.control.NoStackTrace

private class CirceError(cause: io.circe.Error) extends Exception with NoStackTrace {
  override def getMessage: String = cause.show
}
