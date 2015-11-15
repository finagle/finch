package io.finch.benchmarks.service

import io.circe.generic.auto._
import io.finch.circe._

package object circe {
  def userService: UserService = new FinchUserService
}
