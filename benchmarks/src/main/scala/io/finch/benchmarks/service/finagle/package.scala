package io.finch.benchmarks.service

package object finagle {
  def userService: UserService = new FinagleUserService
}
