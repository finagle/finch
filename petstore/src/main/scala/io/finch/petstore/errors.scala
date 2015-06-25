package io.finch.petstore

case class MissingIdentifier(message: String) extends Exception(message)
case class MissingPet(message: String) extends Exception(message)