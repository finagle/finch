package io.finch
package client

import com.twitter.finagle.httpx.{HeaderMap, Response}
import com.twitter.io.Buf

sealed trait Resource[A] { 
  val headers: HeaderMap
  val content: A
  def as[B](implicit ev: A => Buf, d: DecodeResource[B]): Resource[B] = 
    Ok(headers, d(content))
}

object Resource {
  def fromResponse(resp: Response): Resource[Buf] = resp.statusCode match {
    case 200 => Ok(resp.headerMap, resp.content)
    case 201 => Created(resp.headerMap, resp.content)
    case 202 => Accepted(resp.headerMap, resp.content)
    case _   => Shruggie(resp.headerMap, resp.content) 
  }
}

case class Ok[A](headers: HeaderMap, content: A) extends Resource[A]
case class Created[A](headers: HeaderMap, content: A) extends Resource[A]
case class Accepted[A](headers: HeaderMap, content: A) extends Resource[A]

case class Shruggie[A](headers: HeaderMap, content: A) extends Resource[A]
