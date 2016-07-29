package io.finch

import cats.Eq
import com.twitter.finagle.http.Request

/**
 * An input for [[Endpoint]].
 */
final case class Input(request: Request, path: Seq[String]) {
  def headOption: Option[String] = path.headOption
  def drop(n: Int): Input = copy(path = path.drop(n))
  def isEmpty: Boolean = path.isEmpty
}

/**
 * Creates an input for [[Endpoint]] from [[Request]].
 */
object Input {
  def apply(req: Request): Input = Input(req, req.path.split("/").toList.drop(1))

  implicit val inputEq: Eq[Input] = Eq.fromUniversalEquals
}
