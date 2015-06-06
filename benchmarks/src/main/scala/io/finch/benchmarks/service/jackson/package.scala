package io.finch.benchmarks.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.finch.jackson._

package object jackson {
  implicit val objectMapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  def userService: UserService = new FinchUserService
}
