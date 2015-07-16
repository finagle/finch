package io.finch.benchmarks.service

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Response, Request}

abstract class UserService {
  val db: UserDb = new UserDb

  def backend: Service[Request, Response]
}
