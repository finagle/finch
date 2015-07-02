package io.finch.petstore

import com.twitter.util.{Future, Await}
import org.scalatest._
import org.scalatest.prop.Checkers
import org.scalatest.{FlatSpec, Matchers}

/*
Tests for the PetstoreDb class methods
 */

class PetstoreDbSpec extends FlatSpec with Matchers with Checkers {
  val rover = Pet(Some(0), "Rover", Nil, Option(Category(1, "dog")), Option(Nil), Option(Available))
  val jack = Pet(Some(1), "Jack", Nil, Option(Category(1, "dog")), Option(Nil), Option(Available))
  val sue = Pet(None, "Sue", Nil, Option(Category(1, "dog")), Option(Nil), Option(Adopted))
  val sadaharu = Pet(None, "Sadaharu", Nil, Option(Category(1, "inugami")), Option(Nil), Option(Available))
  val despereaux = Pet(None, "Despereaux", Nil, Option(Category(1, "mouse")), Option(Nil), Option(Available))
  val alexander = Pet(None, "Alexander", Nil, Option(Category(1, "mouse")), Option(Nil), Option(Pending))
  val wilbur = Pet(None, "Wilbur", Nil, Option(Category(1, "pig")), Option(Nil), Option(Adopted))
  val cheshire = Pet(None, "Cheshire Cat", Nil, Option(Category(1, "cat")), Option(Nil), Option(Available))
  val crookshanks = Pet(None, "Crookshanks", Nil, Option(Category(1, "cat")), Option(Nil), Option(Available))

  trait DbContext {
    val db = new PetstoreDb()
    Await.ready(db.addPet(rover.copy(id = None)))
    Await.ready(db.addPet(jack.copy(id = None)))
    Await.ready(db.addPet(sue.copy(id = None)))
    db.addPet(sadaharu)
    db.addPet(despereaux)
    db.addPet(alexander)
    db.addPet(wilbur)
    db.addPet(cheshire)
    db.addPet(crookshanks)
  }

  //GET: getPet

  "The Petstore DB" should "allow pet lookup by id" in new DbContext {
    assert(Await.result(db.getPet(0)) === rover)
  }

  //POST: add pet
  it should "allow adding pets" in new DbContext {
    check { (pet: Pet) =>
      val result = for {
        petId <- db.addPet(pet)
        newPet <- db.getPet(petId)
      } yield newPet === pet.copy(id = Some(petId))

      Await.result(result)
    }
  }

  it should "fail appropriately for pet ids that don't exist" in new DbContext {
//    assert(Await.result(db.getPet(1001)) === None)
    Await.result(db.getPet(1001).liftToTry).isThrow shouldBe true//CHECK THIS
  }

  //PUT: Update pet
  it should "allow for the updating of existing pets via new Pet object" in new DbContext {
    check{(pet:Pet) =>
      val betterPet = pet.copy(id = Some(0))
      db.updatePet(betterPet)
      val result = for{
        optPet <- db.getPet(0)
      } yield optPet === betterPet

      Await.result(result)
    }
  }

  it should "fail appropriately for the updating of nonexistant pets" in new DbContext{
    check{(pet: Pet) =>
      val noPet = pet.copy(id = Some(10))
      val f = db.updatePet(noPet)
      Await.result(f.liftToTry).isThrow//CHECK THIS
    }
  }

  //GET: find pets by status
//  it should "allow the lookup of pets by status" in new DbContext{
//    val avail = List(rover, jack, sadaharu, despereaux, cheshire, crookshanks)
//    val pend = List(alexander)
//    val adopt = List(sue, wilbur)
//    assert(Await.result(db.getPetsByStatus(Available) === avail))
//    assert(Await.result(db.getPetsByStatus(Pending) === pend))
//  }

}
