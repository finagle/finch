package io.finch.demo

import io.finch.test.ServiceIntegrationSuite
import org.scalatest.Matchers
import org.scalatest.fixture.FlatSpec

class DemoIntegrationTest extends FlatSpec
  with DemoSuite with ServiceIntegrationSuite with Matchers
