package io.finch.petstore

import com.twitter.util.{Future, Await}
import org.scalatest._
import org.scalatest.prop.Checkers
import org.scalatest.{FlatSpec, Matchers}

/*
Tests for the PetstoreDb class methods
 */

class PetstoreDbSpec extends FlatSpec with Matchers with Checkers {
  val rover = Pet(Some(0), "Rover", Nil, Option(Category(1, "dog")), Option(Seq(Tag(1, "puppy"),
    Tag(2, "white"))), Option(Available))
  val jack = Pet(None, "Jack", Nil, Option(Category(1, "dog")), Option(Seq(Tag(1, "puppy"))),
    Option(Available))
  val sue = Pet(None, "Sue", Nil, Option(Category(1, "dog")), Option(Nil), Option(Adopted))
  val sadaharu = Pet(None, "Sadaharu", Nil, Option(Category(1, "inugami")), Option(Nil), Option(Available))
  val despereaux = Pet(None, "Despereaux", Nil, Option(Category(1, "mouse")), Option(Nil), Option(Pending))
  val alexander = Pet(None, "Alexander", Nil, Option(Category(1, "mouse")), Option(Nil), Option(Pending))
  val wilbur = Pet(None, "Wilbur", Nil, Option(Category(1, "pig")), Option(Nil), Option(Adopted))
  val cheshire = Pet(None, "Cheshire Cat", Nil, Option(Category(1, "cat")), Option(Nil), Option(Available))
  val crookshanks = Pet(None, "Crookshanks", Nil, Option(Category(1, "cat")), Option(Nil), Option(Available))

  trait DbContext {
    val db = new PetstoreDb()
    Await.ready(db.addPet(rover))
    Await.ready(db.addPet(jack))
    Await.ready(db.addPet(sue))
    Await.ready(db.addPet(sadaharu))
    Await.ready(db.addPet(despereaux))
    Await.ready(db.addPet(alexander))
    Await.ready(db.addPet(wilbur))
    Await.ready(db.addPet(cheshire))
    Await.ready(db.addPet(crookshanks))
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
  it should "allow the lookup of pets by status" in new DbContext{
    var avail: Seq[Pet] = Await.result(db.getPetsByStatus(Available))
    var pend: Seq[Pet] = Await.result(db.getPetsByStatus(Pending))
    var adopt: Seq[Pet] = Await.result(db.getPetsByStatus(Adopted))
    for(p <- avail){
      assert(p.status.getOrElse("Invalid Status").equals(Available))
    }
    for(p <- pend){
      assert(p.status.getOrElse("Invalid Status").equals(Pending))
    }
    for(p <- adopt){
      assert(p.status.getOrElse("Invalid Status").equals(Adopted))
    }
  }

  //DELETE: Delete pets from the database
  it should "allow the deletion of existing pets from the database" in new DbContext{
    val sadPet = Pet(None, "Blue", Nil, Option(Category(1, "dog")), Option(Nil), Option(Available))
    db.addPet(sadPet)

    db.deletePet(sadPet.id.getOrElse(-1)) //There WILL be an ID

  }

  it should "fail appropriately if user tries to delete a nonexistant pet" in new DbContext{
    val ghostPet1 = Pet(Option(10), "Teru", Nil, Option(Category(1, "dog")), Option(Nil), Option(Available))
//    println("ghostPet1")
    assert(!Await.result(db.deletePet(ghostPet1.id.getOrElse(-1))))
//    println("ghostPet2")
    val ghostPet2 = Pet(None, "Bozu", Nil, Option(Category(1, "dog")), Option(Nil), Option(Available))
    assert(!Await.result(db.deletePet(ghostPet2.id.getOrElse(-1)))) //Used getOrElse(-1) for endpoints later
  }

  //POST: Update a pet in the store with form data
  it should "be able to update existing pets with form data" in new DbContext {
    db.updatePetViaForm(0, Option("Clifford"), Option(Pending))
    var p: Pet = Await.result(db.getPet(0))
    assert(if (p.name.equals("Clifford")) true else false)
    assert(if (p.status == Option(Pending)) true else false)

    db.updatePetViaForm(0, Option("Rover"), Option(Available))
    db.updatePetViaForm(0, None, Option(Pending))
    p = Await.result(db.getPet(0))
    assert(if (p.name.equals("Rover")) true else false)
    assert(if (p.status == Option(Pending)) true else false)

    db.updatePetViaForm(0, Option("Rover"), Option(Available))
    db.updatePetViaForm(0, Option("Clifford"), None)
    p = Await.result(db.getPet(0))
    assert(if (p.name.equals("Clifford")) true else false)
    assert(if (p.status == Option(Available)) true else false)
  }



  //GET: find pets by tags
  it should "find pets by tags" in new DbContext{
    val puppies = Await.result(db.findPetsByTag(Seq("puppy")))

    puppies shouldBe Seq(rover.copy(id = Some(0)), jack.copy(id = Some(1)))
  }

  it should "find pets by multiple tags" in new DbContext{
    val puppies = Await.result(db.findPetsByTag(Seq("puppy", "white")))

    puppies shouldBe Seq(rover.copy(id = Some(0)))
  }

  //GET: returns the current inventory
  it should "return status counts" in new DbContext{
    val statuses = Await.result(db.getInventory)

    statuses shouldBe Map(Available -> 5, Pending -> 2, Adopted -> 2)
  }

}
