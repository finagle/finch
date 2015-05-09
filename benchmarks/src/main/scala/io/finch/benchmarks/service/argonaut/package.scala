package io.finch.benchmarks.service

import _root_.argonaut._, Argonaut._

package object argonaut {
  implicit val statusCodec: CodecJson[Status] = casecodec1(Status.apply, Status.unapply)("message")
  implicit val userCodec: CodecJson[User] =
    casecodec4(User.apply, User.unapply)("id", "name", "age", "statuses")
  implicit val newUserInfoCodec: CodecJson[NewUserInfo] =
    casecodec2(NewUserInfo.apply, NewUserInfo.unapply)("name", "age")
}
