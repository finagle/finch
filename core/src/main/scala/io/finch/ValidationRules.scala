package io.finch

trait ValidationRules {
  /**
   * A [[ValidationRule]] that makes sure the numeric value is greater than given `n`.
   */
  def beGreaterThan[A](n: A)(implicit ev: Numeric[A]): ValidationRule[A] =
    ValidationRule(s"be greater than $n")(ev.gt(_, n))

  /**
   * A [[ValidationRule]] that makes sure the numeric value is less than given `n`.
   */
  def beLessThan[A](n: A)(implicit ev: Numeric[A]): ValidationRule[A] =
    ValidationRule(s"be less than $n")(ev.lt(_, n))

  /**
   * A [[ValidationRule]] that makes sure the string value is longer than `n` symbols.
   */
  def beLongerThan(n: Int): ValidationRule[String] =
    ValidationRule(s"be longer than $n symbols")(_.length > n)

  /**
   * A [[ValidationRule]] that makes sure the string value is shorter than `n` symbols.
   */
  def beShorterThan(n: Int): ValidationRule[String] =
    ValidationRule(s"be shorter than $n symbols")(_.length < n)

  /**
   * Implicit conversion that allows the same inline rules to be used for required and optional
   * values. If the optional value is non-empty, it gets validated (and validation may fail,
   * producing error), but if it is empty, it is always treated as valid.
   *
   * In order to help the compiler determine the case when inline rule should be converted, the type
   * of the validated value should be specified explicitly.
   *
   * {{{
   *   paramOption("foo").should("be greater than 50") { i: Int => i > 50 }
   * }}}
   *
   * @param fn the underlying function to convert
   */
  implicit def toOptionalInlineRule[A](fn: A => Boolean): Option[A] => Boolean = {
    case Some(value) => fn(value)
    case None => true
  }
}
