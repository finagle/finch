package io.finch

sealed trait Segment

object Segment {

  case class Part(name: String) extends Segment

  case object Wildcard extends Segment

  case object Empty extends Segment

}

sealed trait EndpointMetadata

object EndpointMetadata {

  case class Method(method: com.twitter.finagle.http.Method, a: EndpointMetadata) extends EndpointMetadata

  case class Path(segment: io.finch.Segment) extends EndpointMetadata

  case class Parameter(name: String) extends EndpointMetadata

  case class Parameters(name: String) extends EndpointMetadata

  case class Header(name: String) extends EndpointMetadata

  case class Cookie(name: String) extends EndpointMetadata

  case object Body extends EndpointMetadata

  case class Multipart(name: String) extends EndpointMetadata

  case class Coproduct(a: EndpointMetadata, b: EndpointMetadata) extends EndpointMetadata

  case class Product(a: EndpointMetadata, b: EndpointMetadata) extends EndpointMetadata

  case object Const extends EndpointMetadata

  case object Empty extends EndpointMetadata

}
