package io.finch.petstore

import com.twitter.finagle.Httpx
import com.twitter.util.Await
import io.finch.argonaut._
import io.finch.petstore.endpoint._

class PetstoreApp {
//  println("WELCOME TO PETSTOREAPP!")
  val db = new PetstoreDb()
  db.addPet(Pet(None, "Sadaharu", Nil, Option(Category(1, "inugami")), Option(Nil), Option(Available)))
  db.addPet(Pet(None, "Despereaux", Nil, Option(Category(1, "mouse")), Option(Nil), Option(Available)))
  db.addPet(Pet(None, "Alexander", Nil, Option(Category(1, "mouse")), Option(Nil), Option(Pending)))
  db.addPet(Pet(None, "Wilbur", Nil, Option(Category(1, "pig")), Option(Nil), Option(Adopted)))
  db.addPet(Pet(None, "Cheshire Cat", Nil, Option(Category(1, "cat")), Option(Nil), Option(Available)))
  db.addPet(Pet(None, "Crookshanks", Nil, Option(Category(1, "cat")), Option(Nil), Option(Available)))

//  val store = Get / "store" / "inventory" /> (
//      db.statusCodes.map {
//        _.map { case (k, v) => (k.code, v) }
//      }
//      )

//  val server = Httpx.serve(":8080", (updatePet).toService)

  val service = (updatePet(db) :+: getPetEndpt(db)).toService
  val server = Httpx.serve(":8080", service) //creates service

  Await.ready(server)

  def close() = {
    Await.ready(server.close())
  }
}

object PetstoreApp extends PetstoreApp with App {
  Await.ready(server)
}
