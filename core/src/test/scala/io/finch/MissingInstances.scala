package io.finch

import java.util.UUID

import algebra.Eq
import com.twitter.util.Try

trait MissingInstances {
  implicit def eqTry[A]: Eq[Try[A]] = Eq.fromUniversalEquals
  implicit def eqUUID: Eq[UUID] = Eq.fromUniversalEquals
}
