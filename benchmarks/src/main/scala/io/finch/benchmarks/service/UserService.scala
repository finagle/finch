package io.finch.benchmarks.service

import com.twitter.finagle.Service
import io.finch.{HttpRequest, HttpResponse}

abstract class UserService {
  val db: UserDb = new UserDb

  def backend: Service[HttpRequest, HttpResponse]
}
