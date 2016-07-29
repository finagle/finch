package io.finch.internal

import java.util.UUID

import io.finch.FinchSpec

class CaptureSpec extends FinchSpec {
  checkAll("Capture[String]", CaptureLaws[String].all)
  checkAll("Capture[Int]", CaptureLaws[Int].all)
  checkAll("Capture[Long]", CaptureLaws[Long].all)
  checkAll("Capture[Boolean]", CaptureLaws[Boolean].all)
  checkAll("Capture[UUID]", CaptureLaws[UUID].all)
}
