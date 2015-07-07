package io.finch.demo

import io.finch.test.ServiceSuite
import org.scalatest.Matchers
import org.scalatest.fixture.FlatSpec

class DemoSpec extends FlatSpec with DemoSuite with ServiceSuite with Matchers
