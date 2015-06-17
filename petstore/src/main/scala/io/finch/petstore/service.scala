package io.finch.petstore

import com.twitter.finagle.Service
import com.twitter.util.Future

import io.finch._
import io.finch.petstore.model.UserNotFound
import io.finch.petstore.{Db, AuthRequest}
import io.finch.petstore.model.User

object service{
  import model._
  import reader._

  //PET METHODS/////////////////////////////////////////////////////////
  /*
  POST: Adds a new pet to the store
   */

  /*
  PUT: Updates an existing pet
   */

  /*
  GET: Finds a list of pets with the specified status attribute
   */
  case class GetPetsByStatus(inputStat: String) extends Service[AuthRequest, Seq[Pet]]{
    def apply(req: AuthRequest): Future[Seq[Pet]] = {
      var allMatches = Seq[Pet]()
      for(p <- PetDb.all){
        if(p.status == inputStat) allMatches :+ p
      }
      allMatches
    }
  }


  /*
  GET: Finds pets by tags
   */
  case class GetAllOfCertainTag(getTag: String) extends Service[AuthRequest, Pet]{
    def apply(req: AuthRequest): Future[Seq[Pet]] = {
      var allMatches = Seq[Pet]()
      for(p <- PetDb.all){
        var tagList = p.tags
        if(tagList.contains(getTag)) allMatches :+ p
      }
      allMatches
    }
  }

  /*
  GET: Finds all the tags connected to a certain pet, given the pet's ID
   */
  case class GetAllTags(inputId:Long) extends Service[AuthRequest, Seq[String]]{
    def apply(req: AuthRequest): Future[Seq[String]] = {
      var animal = PetDb.select(inputId)
      animal.tags
    }
  }

  /*
  DELETE: Deletes a pet, given its ID
   */
  case class DeletePet(inputId: Long) extends Service[AuthRequest, Unit]{
    def apply(req: AuthRequest): Future[Unit] = { //not actually sure how to return nothing...
      PetDb.delete(inputId)
    }
  }

  /*
  GET: Finds a pet by its 'id'
   */
  case class GetPet(inputId: Long) extends Service[AuthRequest, Pet]{
    def apply(req: AuthRequest): Future[Pet] = {
      PetDb.select(inputId)
    }
  }

  /*
  POST: Updates a pet in the store from form data
   */

  /*
  POST: Uploads an image of a pet, by adding a new url to the pet's list of images
   */
  case class PostImage(inputId: Long, url: String) extends Service[AuthRequest, Seq[String]]{
    def apply(req: AuthRequest): Future[Seq[String]] = {
      val doubutsu = PetDb.select(inputId) //Not sure why it complains when I make this a var
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