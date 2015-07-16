package io.finch.petstore

import io.finch.test.ServiceIntegrationSuite
import org.scalatest.Matchers
import org.scalatest.fixture.FlatSpec

class PetstoreServiceIntegrationTest extends FlatSpec with Matchers
  with ServiceIntegrationSuite with PetstoreServiceSuite
