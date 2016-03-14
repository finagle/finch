package io.finch

import java.util.UUID

class EncodeSpec extends FinchSpec {
  checkAll("Encode.TextPlain[String]", EncodeLaws.textPlain[String].all)
  checkAll("Encode.TextPlain[Int]", EncodeLaws.textPlain[Int].all)
  checkAll("Encode.TextPlain[Option[Boolean]]", EncodeLaws.textPlain[Option[Boolean]].all)
  checkAll("Encode.TextPlain[List[Long]]", EncodeLaws.textPlain[List[Long]].all)
  checkAll("Encode.TextPlain[Either[UUID, Float]]", EncodeLaws.textPlain[Either[UUID, Float]].all)
}
