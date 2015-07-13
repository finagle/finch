package io.finch.petstore

import com.twitter.finagle.Httpx
import com.twitter.util.{Future, Await}
import io.finch.argonaut._
import io.finch.petstore.endpoint._

/**
 * PetstoreApp runs the PetstoreAPI service. It is the hub where all the endpoints that give users access to API
 * methods are connected to the service itself, which is launched on port :8080.
 */
class PetstoreApp {
  val db = new PetstoreDb()
  db.addPet(Pet(None, "Sadaharu", Nil, Some(Category(None, "inugami")), Some(Nil), Some(Available)))
  db.addPet(Pet(None, "Despereaux", Nil, Some(Category(None, "mouse")), Some(Nil), Some(Available)))
  db.addPet(Pet(None, "Alexander", Nil, Some(Category(None, "mouse")), Some(Nil), Some(Pending)))
  db.addPet(Pet(None, "Wilbur", Nil, Some(Category(None, "pig")), Some(Nil), Some(Adopted)))
  db.addPet(Pet(None, "Cheshire Cat", Nil, Some(Category(None, "cat")), Some(Nil), Some(Available)))
  db.addPet(Pet(None, "Crookshanks", Nil, Some(Category(None, "cat")), Some(Nil), Some(Available)))

  val service = endpoint.makeService(db)

  val server = Httpx.serve(":8080", service) //creates service

  Await.ready(server)

  def close(): Future[Unit] = {
    Await.ready(server.close())
  }
}

/**
 * Launches the PetstoreAPI service when the system is ready.
 */
object PetstoreApp extends PetstoreApp with App {
  Await.ready(server)
}
