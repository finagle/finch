import _root_.argonaut._, Argonaut._
import io.finch.petstore.model._

/*
Codec for Pets
 */



implicit def petCodec: CodecJson[Pet] = //instance of a type class
  casecodec6(Pet.apply, Pet.unapply)("id", "category", "name", "photoUrls", "tags", "status")

//val petEncode: EncodeJson[Pet] = {
//  jencode6L((p: Pet) => (p.id, p.category, p.name, p.photoUrls, p.tags, p.status))
//  ("id", "category", "name", "photoUrls", "tags", "status")
//}

//Method 1:
//val petDecode: DecodeJson[Pet] =
//  DecodeJson(c => for {
//    id <- (c --/ "id").as[Long]
//    category <- (c --/ "category").as[String]
//    name <- (c --/ "name").as[String]
//    photoUrls <- (c --/ "photoUrls").as[Seq[String]]
//    tags <- (c --/ "tags").as[Seq[String]]
//    status <- (c --/ "status").as[String]
//  } yield Pet(id, category, name, photoUrls, tags, status))

//Method 2:
//val petDecode: DecodeJson[Pet] => Pet =
//  jdecode6L(Pet.apply)("id", "category", "name", "photoUrls", "tags", "status")

