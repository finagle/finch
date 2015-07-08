package io.finch.petstore

import io.finch.argonaut._
import io.finch.request._

object reader {


  //  implicit val petReader: RequestReader[Pet] = body.as[ Pet ]

  implicit val statusReader: RequestReader[ Status ] = param("status").map(Status.fromString)

  implicit val tagReader: RequestReader[ Seq[ String ] ] = param("tags").map { tags =>
    tags.split(",").map(_.trim)
  }

  implicit val nameReader: RequestReader[ String ] = param("name")

//  implicit val createUsersReader: RequestReader[ Seq[ User ] ] = {
//    val uSeq: RequestReader[Seq[User]] = body.as[Seq[User]]
//    val newSeq: Seq[User] = Seq.empty[User]
        /*
     ======  ===    ||====     ===
       ||  ||   ||  ||    || ||   ||
       ||  ||   ||  ||    || ||   ||
       ||    ===    ||====     ====
      */
//    for{
//      u <- uSeq
//      u.as[User]
//    } yield u
//    uSeq.embedFlatMap{
//      _.as[User]
//    }

//    body.as[Seq].map(_.as[User])
//  }

}