package io.finch.petstore

import com.twitter.util.{Await, Future}

import scala.collection.mutable
//import scala.concurrent.Await

class PetstoreDb {
  private[this] val pets = mutable.Map.empty[Long, Pet]
  private[this] val tags = mutable.Map.empty[Long, Tag]
  private[this] val cat = mutable.Map.empty[Long, Category]
  //how efficient is this? compared to hashtable/map? I would think fetching from a hash*** would be faster

  def failIfEmpty(o: Option[Pet]): Future[Pet] = o match {
    case Some(pet) => Future.value(pet)
    case None => Future.exception(MissingPet("No pet!"))
  } //move this inside getPet


  def getPet(id: Long): Future[Pet] = Future(
    pets.synchronized {
      pets.getOrElse(id, throw MissingPet("Your pet doesn't exist! :("))
    }
  )

  //POST: Add pet
  def addPet(inputPet: Pet): Future[Long] = Future.value(
    pets.synchronized {
      val id = if (pets.isEmpty) 0 else pets.keys.max + 1
      pets(id) = inputPet.copy(id = Some(id))
      id
    }
  )

  //PUT: Update existing pet given a pet object
  //@return: updated pet

  def updatePet(inputPet: Pet): Future[Pet] = inputPet.id match {
    case Some(id) =>
      if(pets.contains(id)) pets.synchronized{
        pets(id) = inputPet
        Future.value(inputPet)
      } else {
        Future.exception(MissingPet("Invalid id: doesn't exist"))
      }
    case None => Future.exception(MissingIdentifier(s"Missing id for pet: $inputPet"))
    //    case None => Future.exception(MissingIdentifier(s"Missing id for pet: $inputPet"))
  }

  def allPets: Future[List[Pet]] = Future.value(
    pets.synchronized(pets.toList.sortBy(_._1).map(_._2))
  )

  //GET: find pets by status
  def getPetsByStatus(s: Status): Future[Seq[Pet]] = {
    val allMatchesFut = for{
      petList <- allPets //List[Pet]
      allBool = petList.map(_.status)
    } yield petList.filter(_.status.map(_.code.equals(s.code)).getOrElse(false)) //This better be legal...
   allMatchesFut
  }

  //GET: find pets by tags
  //Muliple tags can be provided with comma seperated strings.
//  def findPetsByTag(req: HttpRequest): Future[List[Pet]] = {
//
//  }


//  case class FindPetsByTag() extends Service[HttpRequest, Seq[Pet]]{
//    def apply(req: HttpRequest): Future[Seq[Pet]] = {
//      val allMatches = Seq[Pet]()
//      val actualTags = Seq[Tag]()


//
//      for{matchTags <- tagReader(req) //Seq[String]
//        t <- matchTags //String
//        allTags <- TagDb.all //Seq[Tag]
//        singleTag <- allTags //Tag
//        if(singleTag.name.equals(t))
//      }yield actualTags :+ singleTag
//      //actualTags should now be populated with the tags we want to match
//      for{petList <- PetDb.all //List[Pet]
//        p <- petList //Pet
//        if (actualTags.forall(p.tags.contains)) //if actualTags is subset of p.tags
//      }yield allMatches :+ p
//    }
//  }

  //DELETE
  def deletePet(id: Long): Future[Boolean] = Future.value(
    pets.synchronized {
      if (pets.contains(id)) {
        pets.remove(id)
        true
      } else false
    }
  )

  //GET: Find pet by ID

  //POST: Update a pet in the store with form data

  //POST: Upload an image

  //Returns the current inventory
//  def statusCodes: Future[Map[Status, Int]] = Future.value(
//    pets.synchronized {
//      pets.groupBy(_._2.status).map {
//        case (status, kvs) => (status, kvs.size)
//      }
//    }
//  )
}

//object PetstoreDb{}
