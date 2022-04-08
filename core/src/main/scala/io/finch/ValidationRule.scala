package io.finch

/** A `ValidationRule` enables a reusable way of defining a validation rules in the application domain. It might be composed with [[Endpoint]]s using either
  * should` or `shouldNot` methods and with other `ValidationRule`s using logical methods `and` and `or`.
  *
  * {{{
  *   case class User(name: String, age: Int)
  *   val user: Endpoint[User] = (
  *     param("name").validate(beLongerThan(3)) ::
  *     param("age").as[Int].should(beGreaterThan(0) and beLessThan(120))
  *   ).as[User]
  * }}}
  */
trait ValidationRule[A] { self =>

  /** Text description of this validation rule.
    */
  def description: String

  /** Applies the rule to the specified value.
    *
    * @return
    *   true if the predicate of this rule holds for the specified value
    */
  def apply(value: A): Boolean

  /** Combines this rule with another rule such that the new rule only validates if both the combined rules validate.
    *
    * @param that
    *   the rule to combine with this rule
    *
    * @return
    *   a new rule that only validates if both the combined rules validate
    */
  def and(that: ValidationRule[A]): ValidationRule[A] =
    ValidationRule(s"${self.description} and ${that.description}")(value => self(value) && that(value))

  /** Combines this rule with another rule such that the new rule validates if any one of the combined rules validates.
    *
    * @param that
    *   the rule to combine with this rule
    *
    * @return
    *   a new rule that validates if any of the combined rules validates
    */
  def or(that: ValidationRule[A]): ValidationRule[A] =
    ValidationRule(s"${self.description} or ${that.description}") { value =>
      self(value) || that(value)
    }
}

/** Allows the creation of reusable validation rules for [[Endpoint]]s.
  */
object ValidationRule {

  /** Implicit conversion that allows the same [[ValidationRule]] to be used for required and optional values. If the optional value is non-empty, it gets
    * validated (and validation may fail, producing an error), but if it is empty, it is always treated as valid.
    *
    * @param rule
    *   the validation rule to adapt for optional values
    *
    * @return
    *   a new validation rule that applies the specified rule to an optional value in case it is not empty
    */
  implicit def toOptionalRule[A](rule: ValidationRule[A]): ValidationRule[Option[A]] =
    ValidationRule(rule.description) {
      case Some(value) => rule(value)
      case None        => true
    }

  /** Creates a new reusable [[ValidationRule]] based on the specified predicate.
    *
    * @param desc
    *   text describing the rule being validated
    * @param p
    *   returns true if the data is valid
    *
    * @return
    *   a new reusable validation rule.
    */
  def apply[A](desc: String)(p: A => Boolean): ValidationRule[A] = new ValidationRule[A] {
    def description: String = desc
    def apply(value: A): Boolean = p(value)
  }
}
