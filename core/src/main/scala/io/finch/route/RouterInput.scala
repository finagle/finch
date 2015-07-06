package io.finch.route

import com.twitter.finagle.httpx.Request

/**
 * An input for [[Router]].
 */
final case class RouterInput(request: Request, path: Seq[String]) {
  def headOption: Option[String] = path.headOption
  def drop(n: Int): RouterInput = copy(path = path.drop(n))
}

object RouterInput {
  def apply(req: Request): RouterInput = RouterInput(req, req.path.split("/").toList.drop(1))
}
