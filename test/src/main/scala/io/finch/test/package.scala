package io.finch

import com.twitter.util.Await

package object test {
  /**
   * Syntax for testing an [[Endpoint.Mapped]].
   */
  implicit class MappedEndpointOps[In, A](endpoint: Endpoint.Mapped[In, A]) {
    def lift(in: In): A = Await.result(endpoint.underlying(in)).value
  }
}
