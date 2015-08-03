package io.finch.benchmarks.service

import io.finch.circe._
import io.circe.generic.auto._

package object circe {
  def userService: UserService = new FinchUserService
}
