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

  //Helper: Generates a default id if not given a valid one
//  def idGenerator(obj: Any, mapper: Map[Long, Any]): Future[Long] = Future.value{
    //Assume first param is always the id param
//    val genId = if (mapper.isEmpty) 0 else mapper.keys.max + 1
//    val inputId: Long = obj.id
//    obj match{
//      case (Some(givenId), _*) => {
//        val genId = if(mapper.isEmpty) 0 else mapper.keys.max + 1
//        val inputId: Long = givenId
//        val id: Long = if (inputId != None){
//        if (mapper.exists(_._1 == inputId)) genId else inputId.getOrElse(genId)
//        } else{genId}
//      }
//
//      case (None, _*) => None
//    }
//  }

  //Helper: Adds tag to tag map
  def addTag(inputTag: Tag): Future[Tag] = Future.value(
    tags.synchronized {
      val genId = if (tags.isEmpty) 0 else tags.keys.max + 1
      val inputId: Long = inputTag.id
      val realId: Long = if (inputId != None) {
        if (tags.exists(_._1 == inputId)) genId else inputId
      } else {
        genId
      }
      tags(realId) = inputTag.copy(id = realId)
      inputTag
    }
  )

  //POST: Add pet
  def addPet(inputPet: Pet): Future[Long] = Future.value(
    pets.synchronized {
      val genId = if (pets.isEmpty) 0 else pets.keys.max + 1
      val inputId: Option[Long] = inputPet.id
      val id: Long = if (inputId != None) {
        if (pets.exists(_._1 == inputId)) genId else inputId.getOrElse(genId) //repetition guard
      } else genId
      pets(id) = inputPet.copy(id = Some(id))
      //Add tags into tag map
//        for{
//          tagList <- inputPet.tags
//          t <- tagList
//        } yield addTag(t)
      inputPet.tags match{
        case Some(tagList) => tagList.map(addTag(_))
        case None => None
      }
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
//  def getPetsByStatus(s: Status): Future[Seq[Pet]] = {
//    val allMatchesFut = for{
//      petList <- allPets //List[Pet]
//    } yield petList.filter(_.status.flatMap(_.code.equals(s.code)))
//    allMatchesFut
//  }
  //This works:
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
    val matchPets = for {
      p <- pets.values
      tagList <- p.tags
      pTagStr = tagList.map(_.name)
      if(findTags.forall(pTagStr.contains))
    } yield p
    
    Future(matchPets.toSeq.sortBy(_.id))

//    val matchPets = for{
//      p <- pets.values
//      tagList <- p.tags
//      pTagStr = tagList.map(_.name)
//      if(findTags.forall(pTagStr.contains))
//    } yield p
//
//    Future(matchPets.toSeq.sortBy(_.id))
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

  //POST: Update a pet in the store with form data
  /*
     ======  ===    ||====     ===
       ||  ||   ||  ||    || ||   ||
       ||  ||   ||  ||    || ||   ||
       ||    ===    ||====     ====
      */


  //POST: Upload an image
  /*
     ======  ===    ||====     ===
       ||  ||   ||  ||    || ||   ||
       ||  ||   ||  ||    || ||   ||
       ||    ===    ||====     ====
      */
  //+++++++++++++++++++++++++++++STORE METHODS BEGIN HERE+++++++++++++++++++++++++++++++++++++++++

  //GET: Returns the current inventory
  def getInventory: Future[Map[Status, Int]] = Future.value(
    pets.synchronized {
      pets.groupBy(_._2.status).flatMap {
        case (Some(status), keyVal) => Some(status -> keyVal.size)
        case (None, _) => None
      }
    }
  )
//  def getInventory: Future[Map[Status, Int]] = Future.value(
//    pets.synchronized {
//      pets.groupBy(_._2.status).map {
//        case (status, keyVal) => (status.getOrElse(Available), keyVal.size)
//        case (None, _) => None
//      }
//    }
//  )

  //POST: Place an order for a pet
//  def postOrder(order: Order): Future[Order] = Future.value{
//    orders.synchronized{
//      orders.
//    }
//  }

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

