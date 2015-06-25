package io.finch.petstore

import com.twitter.util.Await
import io.finch._
import org.scalatest.prop.Checkers
import org.scalatest.{FlatSpec, Matchers}
import io.finch.petstore.endpoint.failIfEmpty

/*
Tests for the PetstoreDb class methods
 */

class PetstoreDbSpec extends FlatSpec with Matchers with Checkers {
  val rover = Pet(Some(0), "Rover", Nil, Option(Category(1, "dog")), Option(Nil), Option(Available))
  val jack = Pet(Some(1), "Jack", Nil, Option(Category(1, "dog")), Option(Nil), Option(Available))
  val sue = Pet(None, "Sue", Nil, Option(Category(1, "dog")), Option(Nil), Option(Adopted))

  trait DbContext {
    val db = new PetstoreDb()
    Await.ready(db.addPet(rover.copy(id = None)))
    Await.ready(db.addPet(jack.copy(id = None)))
    Await.ready(db.addPet(sue.copy(id = None)))
  }

  //GET: getPet

  "The Petstore DB" should "allow pet lookup by id" in new DbContext {
    assert(Await.result(db.getPet(0)) === Some(rover))
  }

  //POST: add pet
  it should "allow adding pets" in new DbContext {
    check { (pet: Pet) =>
      val result = for {
        petId <- db.addPet(pet)
        newPet <- db.getPet(petId)
      } yield newPet === Some(pet.copy(id = Some(petId)))

      Await.result(result)
    }
  }

  it should "fail appropriately for pet ids that don't exist" in new DbContext {
    assert(Await.result(db.getPet(1001)) === None)
  }

  //PUT: Update pet
  it should "allow for the updating of existing pets via new Pet object" in new DbContext {
    check{(pet:Pet) =>
      val betterPet = pet.copy(id = Some(0))
      db.updatePet(betterPet)
      val result = for{
        optPet <- db.getPet(0)

        petPet <- failIfEmpty(optPet)
      } yield petPet === betterPet

      Await.result(result)
    }
  }

  it should "fail appropriately for the updating of nonexistant pets" in new DbContext{
    check{(pet: Pet) =>
      val noPet = pet.copy(id = Some(10))
      val f = db.updatePet(noPet)
      Await.result(f.liftToTry).isReturn
    }
  }

  //GET: find pets by status

}
