package io.finch

import cats.data.Xor
import com.twitter.finagle.httpx.path.Path

package object client {

  type Result[A] = String Xor Resource[A]

  case class FinchRequest(conn: Connection, path: Path)
}
