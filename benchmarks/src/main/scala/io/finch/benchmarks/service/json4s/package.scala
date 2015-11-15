package io.finch.benchmarks.service

import io.finch.json4s._
import org.json4s.{DefaultFormats, Formats}

package object json4s {
  implicit val formats: Formats = DefaultFormats

  def userService: UserService = new FinchUserService
}
