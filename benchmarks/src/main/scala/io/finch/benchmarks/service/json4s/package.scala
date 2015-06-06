package io.finch.benchmarks.service

import org.json4s.{DefaultFormats, Formats}
import io.finch.json4s._

package object json4s {
  implicit val formats: Formats = DefaultFormats

  def userService: UserService = new FinchUserService
}
