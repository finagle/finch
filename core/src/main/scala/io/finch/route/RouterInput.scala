package io.finch.route

import io.finch.HttpRequest

/**
 * An input for [[Router]].
 */
final case class RouterInput(request: HttpRequest, path: Seq[String]) {
  def headOption: Option[String] = path.headOption
  def drop(n: Int): RouterInput = copy(path = path.drop(n))
}

object RouterInput {
  def apply(req: HttpRequest): RouterInput = RouterInput(req, req.path.split("/").toList.drop(1))
}
