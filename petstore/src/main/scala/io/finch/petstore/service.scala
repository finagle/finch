package io.finch.petstore

import com.twitter.finagle.Service
import com.twitter.util.{Await, Future}

import io.finch._
import io.finch.petstoreDemo.{CategoryDb, TagDb, Id, PetDb}

object service{
  import io.finch.petstore.model._
  import reader._
  import io.finch.petstore._

  //PET METHODS/////////////////////////////////////////////////////////
  /*
  POST: Adds a new pet to the store
   */
  case class AddPet() extends Service[HttpRequest, Pet]{
    def apply(req: HttpRequest): Future[Pet] = {
      var futurePet: Future[Pet] = petReader(req)
      var p: Pet = Await.result(futurePet)
      var id: Long = if (p.id == None) Id() else p.id.to[Long] //sketchy conversion....
      PetDb.insert(id, p)
      //Add new tags to TagDb
      for (t <- p.tags.get){ //?? Why does this one need to be assured it's getting a Seq[Tag] but Category doesn't??
        if (TagDb.select(t.id) == None) TagDb.insert(t.id, t)
      }
      //Add new categories to CategoryDb
      for (c <- p.category){
        if (CategoryDb.select(c.id) == None) CategoryDb.insert(c.id, c)
      }
      futurePet
    }
  }

  /*
  PUT: Updates an existing pet. Note that id will never be changed.
   */
  case class UpdatePet() extends Service[HttpRequest, Pet]{
    def apply(req: HttpRequest): Future[Pet] = {
      val futurePet: Future[Pet] = petReader(req) //don't use this
      val modInfo: Pet = Await.result(futurePet)
//      val longId: Long = modInfo.id.getOrElse(-1L)
      val id = modInfo.id match{
        case Some(x) => Future.value(x)
        case None => Future.exception(new Exception("Pet to modify does not have an id."))
      }



      val oldPet: Pet = Await.result(PetDb.select(id)).get //deprecated?
      //Getting updated params
      val category = if (modInfo.tags == None) oldPet.category else modInfo.category
      val name = if (modInfo.name == None) oldPet.name else modInfo.name
      val photoUrls = if (modInfo.photoUrls == None) oldPet.photoUrls else modInfo.photoUrls
      val tags = if (modInfo.tags == None) oldPet.tags else modInfo.tags
      val status = if (modInfo.status == None) oldPet.status else modInfo.status
      //End getting updated params
      val betterPet = Pet(modInfo.id, category, name, photoUrls, tags, status)
      PetDb.insert(id, betterPet) //override old pet with a new, updated one
      futurePet
    }
  }

  /*
  GET: Finds a list of pets with the specified status attribute
   */
  case class GetPetsByStatus() extends Service[HttpRequest, Seq[Pet]]{
    def apply(req: HttpRequest): Future[Seq[Pet]] = {
      val allMatches = Seq[Pet]()
      val getStat = statusReader(req)
      for( p <- Await.result(PetDb.all)){
        if (p.status.equals(getStat)) allMatches :+ p
      }
      Future(allMatches)
    }
  }

  /*
  GET: Finds pets by tags
    Given: Sequence of Strings representing tags
    -> Go through each tag in input list and find its corresponding actual TagDb Tag
    -> Create a list of these actual tags
    -> Go through all existing Pets and check if List is a subset of its tag list (must be some method...)
   */
  case class GetAllPetsOfTag() extends Service[HttpRequest, Seq[Pet]]{
    def apply(req: HttpRequest): Future[Seq[Pet]] = {
      val tagStrings = tagReader(req)
      val matchTags = Seq[Tag]()

    }
  }

//  case class GetAllOfCertainTag(getTag: String) extends Service[AuthRequest, Pet]{
//    def apply(req: AuthRequest): Future[Seq[Pet]] = {
//      var allMatches = Seq[Pet]()
//      for(p <- PetDb.all){
//        var tagList = p.tags
//        if(tagList.contains(getTag)) allMatches :+ p
//      }
//      allMatches
//    }
//  }

  /*
  GET: Finds all the tags connected to a certain pet, given the pet's ID
   */
//  case class GetAllTags(inputId:Long) extends Service[AuthRequest, Seq[String]]{
//    def apply(req: AuthRequest): Future[Seq[String]] = {
//      var animal = PetDb.select(inputId)
//      animal.tags
//    }
//  }

  /*
  DELETE: Deletes a pet, given its ID
   */
  case class DeletePet(inputId: Long) extends Service[HttpRequest, Unit]{
    def apply(req: HttpRequest): Future[Unit] = { //not actually sure how to return nothing...
      PetDb.delete(inputId)
    }
  }

  /*
  GET: Finds a pet by its 'id'
   */
  case class GetPet(inputId: Long) extends Service[HttpRequest, Pet]{
    def apply(req: HttpRequest): Future[Pet] = {
      PetDb.select(inputId)
    }
  }

  /*
  POST: Updates a pet in the store from form data
   */

  /*
  POST: Uploads an image of a pet, by adding a new url to the pet's list of images
   */
  case class PostImage(inputId: Long, url: String) extends Service[HttpRequest, Seq[String]]{
    def apply(req: HttpRequest): Future[Seq[String]] = {
      val doubutsu = PetDb.select(inputId)
      doubutsu.photoUrls :+ url
    }
  }

  //STORE METHODS/////////////////////////////////////////////////////////

  /*
  GET: Returns pet inventories of the store. Inventory is organized by the pets' status attributes
   */

  /*
  POST: Places an order for a pet
   */

  /*
  DELETE: Deletes a purchase order by ID
   */

  /*
  GET: Finds a purchase order by ID
   */

  //USER METHODS/////////////////////////////////////////////////////////

  /*
  GETs a user with a certain 'id'
   */
//  case class GetUser(userId: Long) extends Service[AuthRequest, User] {
//    def apply(req: AuthRequest): Future[User] = Db.select(userId) flatMap {
//      case Some(inputUser) => inputUser.toFuture
//      case None => UserNotFound(userId).toFutureException[User]
//    }
//  }

  /*
  GETs a user by username
   */
//  case class GetUser(username: String) extends Service[AuthRequest, User] {
//    def apply(req: AuthRequest): Future[User] = {
//      var allMatch = List[User]() //empty list
//      for {id <- Db.all}
//          if (Db.select(id).username == username) allMatch.::(Db.select(id))
//      if (allMatch.length > 0) allMatch
//      else UserNotFound(id).toFutureException[User]
//    }
//  }

  /*
  POST: Creates a new user based on form information (excluding id, which is auto-generated)
   */
//  case class CreateUser(username: String, firstName: String, lastName: String,
//      email: String, password: String, phone: String) extends Service[AuthRequest, User] {
//    def apply(req: AuthRequest): Future[User] = {
//      var id = Id() //Why is this an error??
//      var newUser = User(Id(), username, firstName, lastName, email, password, phone)
//      Db.insert(id, newUser)
//    }
//  }

  /*
  POST: A REST service that creates a list of users with a given input array
   */

  /*
  POST: A REST service that creates a list of users with a given input List
   */

  /*
  GET: Logs user into the system
   */

  /*
  GET: Logs out current logged in user session
   */

  /*
  DELETE: Deletes a user
   */

  /*
  PUT: Updates a user
   */
}