package io.finch.circe

import scala.util.control.NoStackTrace

import cats.syntax.show._

private class CirceError(cause: io.circe.Error) extends Exception with NoStackTrace {
  override def getMessage: String = cause.show
}
