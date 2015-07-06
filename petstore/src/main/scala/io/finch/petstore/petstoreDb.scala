package io.finch.petstore

import com.twitter.util.{Await, Future}

import scala.collection.mutable
//import scala.concurrent.Await

class PetstoreDb {
  private[this] val pets = mutable.Map.empty[Long, Pet]
  private[this] val tags = mutable.Map.empty[Long, Tag]
  private[this] val cat = mutable.Map.empty[Long, Category]
  private[this] val orders = mutable.Map.empty[Long, Order]

  def failIfEmpty(o: Option[Pet]): Future[Pet] = o match {
    case Some(pet) => Future.value(pet)
    case None => Future.exception(MissingPet("No pet!"))
  } //move this inside getPet

  //GET: Find pet by ID
  def getPet(id: Long): Future[Pet] = Future(
    pets.synchronized {
      pets.getOrElse(id, throw MissingPet("Your pet doesn't exist! :("))
    }
  )

  //POST: Add pet
  def addPet(inputPet: Pet): Future[Long] = Future.value(
    pets.synchronized {
      val genId = if (pets.isEmpty) 0 else pets.keys.max + 1
      val inputId: Option[Long] = inputPet.id
      val id: Long = if (inputId != None) {
        if (pets.exists(_._1 == inputId)) genId else inputId.getOrElse(genId) //repetition guard
      } else{genId}
      pets(id) = inputPet.copy(id = Some(id))
      //Add tags into tag map
      /*
      ======  ===    ||====     ===
        ||  ||   ||  ||    || ||   ||
        ||  ||   ||  ||    || ||   ||
        ||    ===    ||====     ====
       */
      //End add tags
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
    } yield petList.filter(_.status.map(_.code.equals(s.code)).getOrElse(false))
   allMatchesFut
  }

  //GET: find pets by tags
  //Muliple tags can be provided with comma seperated strings.
  def findPetsByTag(findTags: Seq[String]): Future[Seq[Pet]] = {

    //map, filter, flatMap is safer----fix this, stay away from mutable collections
    //filter, exists

    //create true/false

    //What do we know?
    //findTags = Sequence of Strings
    //All pets have a Sequence of Tags
    //We need to find the pets that have findTags in their sequence of tags
    //You cannot simply create a tag without giving it a valid id first => Cannot just turn all strings into tags and compare
    //To do that, we could:
    /*
      - Turn findTags into a sequence of Tags --> use findTags.forall(p.tags.contains) as a filter method
     */

//    val realTags =


    val matchPets = for {
      p <- pets.values
      tagList <- p.tags
      pTagStr = tagList.map(_.name)
      if(findTags.forall(pTagStr.contains))
    } yield p
    
    Future(matchPets.toSeq.sortBy(_.id))

//      val testcopy = for{(k,v) <- testMap;if (v.name.length > 6)} yield v
//        - Gives back arrayBuffer...which is mutable
//     */

//    val realTags: Map[Long,Tag] = tags.retain((k,v) => findTags.contains(v.name))
    //this won't work, since tags is private. Operating directly on it returns PetstoreDb.this.tags.type....need another way
    //Try this: go through each pet's list of tags and turn THAT into a list of strings
    //pet.tags.map(_.name)
    //New menace: p.tags = Option(p.tags)

    val matchPets = for{
      p <- pets.values
      tagList <- p.tags
      pTagStr = tagList.map(_.name)
      if(findTags.forall(pTagStr.contains))
    } yield p
    
    Future(matchPets.toList)
//    Future(allMatches.toList)*/
  }

  //DELETE
  def deletePet(id: Long): Future[Boolean] = Future.value(
    pets.synchronized {
      if (pets.contains(id)) {
        pets.remove(id)
        true
      } else false
    }
  )

  def getInventory: Future[Map[Status, Int]] = Future.value(
    pets.synchronized {
      pets.groupBy(_._2.status).flatMap {
        case (Some(status), keyVal) => Some(status -> keyVal.size)
        case (None, _) => None
      }
    }
  )

  //POST: Update a pet in the store with form data

  //POST: Upload an image

  //+++++++++++++++++++++++++++++STORE METHODS BEGIN HERE+++++++++++++++++++++++++++++++++++++++++

  //GET: Returns the current inventory
//  def getInventory: Future[Map[Status, Int]] = Future.value(
//    pets.synchronized {
//      pets.groupBy(_._2.status).map {
//        case (status, keyVal) => (status.getOrElse(Available), keyVal.size)
//        case (None, _) => None
//      }
//    }
//  )
//  def statusCodes: Future[Map[Status, Int]] = Future.value(
//    pets.synchronized {
//      pets.groupBy(_._2.status).map {
//        case (status, kvs) => (status, kvs.size)
//      }
//    }
//  )

  //POST: Place an order for a pet


  //DELETE: Delete purchase order by ID

  //GET: Find purchase order by ID

  //============================STORE METHODS END HERE================================================

  //+++++++++++++++++++++++++++++USER METHODS BEGIN HERE+++++++++++++++++++++++++++++++++++++++++

  //POST: Create user

  //POST: Create list of users with given input array

  //POST: Create list of users with given input list

  //GET: Logs user into system

  //GET: Logs out current logged in user session

  //DELETE: Delete user

  //GET: Get user by username

  //PUT: Update user

  //============================USER METHODS END HERE================================================

}

