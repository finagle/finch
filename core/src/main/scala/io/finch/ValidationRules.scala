package io.finch

trait ValidationRules {

  /** A [[ValidationRule]] that makes sure the numeric value is greater than given `n`.
    */
  def beGreaterThan[A](n: A)(implicit ev: Numeric[A]): ValidationRule[A] =
    ValidationRule(s"be greater than $n")(ev.gt(_, n))

  /** A [[ValidationRule]] that makes sure the numeric value is less than given `n`.
    */
  def beLessThan[A](n: A)(implicit ev: Numeric[A]): ValidationRule[A] =
    ValidationRule(s"be less than $n")(ev.lt(_, n))

  /** A [[ValidationRule]] that makes sure the string value is longer than `n` symbols.
    */
  def beLongerThan(n: Int): ValidationRule[String] =
    ValidationRule(s"be longer than $n symbols")(_.length > n)

  /** A [[ValidationRule]] that makes sure the string value is shorter than `n` symbols.
    */
  def beShorterThan(n: Int): ValidationRule[String] =
    ValidationRule(s"be shorter than $n symbols")(_.length < n)
}
