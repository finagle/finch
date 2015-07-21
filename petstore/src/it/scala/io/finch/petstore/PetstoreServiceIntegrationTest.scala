package io.finch.petstore

import io.finch.test.ServiceIntegrationSuite
import org.scalatest.Matchers
import org.scalatest.fixture.FlatSpec

class PetstorePetServiceIntegrationTest extends FlatSpec with Matchers
  with ServiceIntegrationSuite with PetstorePetServiceSuite

class PetstoreStoreServiceIntegrationTest extends FlatSpec with Matchers
  with ServiceIntegrationSuite with PetstoreStoreServiceSuite

class PetstoreUserServiceIntegrationTest extends FlatSpec with Matchers
  with ServiceIntegrationSuite with PetstoreUserServiceSuite
