package io.finch.refined

import scala.util.control.NoStackTrace

case class PredicateFailed(error: String) extends Exception with NoStackTrace {

    override def getMessage: String = error

}
