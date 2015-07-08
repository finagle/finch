package io.finch.petstore

import com.twitter.finagle.Httpx
import com.twitter.util.Await
import io.finch.argonaut._
import io.finch.petstore.endpoint._

class PetstoreApp {
//  println("WELCOME TO PETSTOREAPP!")
  val db = new PetstoreDb()
  db.addPet(Pet(None, "Sadaharu", Nil, Some(Category(1, "inugami")), Some(Nil), Some(Available)))
  db.addPet(Pet(None, "Despereaux", Nil, Some(Category(1, "mouse")), Some(Nil), Some(Available)))
  db.addPet(Pet(None, "Alexander", Nil, Some(Category(1, "mouse")), Some(Nil), Some(Pending)))
  db.addPet(Pet(None, "Wilbur", Nil, Some(Category(1, "pig")), Some(Nil), Some(Adopted)))
  db.addPet(Pet(None, "Cheshire Cat", Nil, Some(Category(1, "cat")), Some(Nil), Some(Available)))
  db.addPet(Pet(None, "Crookshanks", Nil, Some(Category(1, "cat")), Some(Nil), Some(Available)))

//  val server = Httpx.serve(":8080", (updatePet).toService)

  val service = (updatePetEndpt(db) :+: getPetEndpt(db) :+: uploadImageEndpt(db)).toService
  val server = Httpx.serve(":8080", service) //creates service

  Await.ready(server)

  def close() = {
    Await.ready(server.close())
  }
}

object PetstoreApp extends PetstoreApp with App {
  Await.ready(server)
}
