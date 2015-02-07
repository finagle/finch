/*
 * Copyright 2014, by Vladimir Kostyukov and Contributors.
 *
 * This file is a part of a Finch library that may be found at
 *
 *      https://github.com/finagle/finch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributor(s):
 * Ben Whitehead
 * Ryan Plessner
 * Pedro Viegas
 * Jens Halm
 */

package io.finch.request

/**
 * A reusable validation rule that can be applied to any [[RequestReader]] with a matching type.
 */
trait ValidationRule[A] { self =>

  /**
   * Text description of this validation rule.
   */
  def description: String

  /**
   * Applies the rule to the specified value.
   *
   * @return true if the predicate of this rule holds for the specified value
   */
  def apply(value: A): Boolean

  /**
   * Combines this rule with another rule such that the new
   * rule only validates if both the combined rules validate.
   *
   * @param that the rule to combine with this rule
   * @return a new rule that only validates if both the combined rules validate
   */
  def and(that: ValidationRule[A]): ValidationRule[A] =
    ValidationRule(s"${self.description} and ${that.description}") { value => self(value) && that(value) }

  /**
   * Combines this rule with another rule such that the new
   * rule validates if any one of the combined rules validates.
   *
   * @param that the rule to combine with this rule
   * @return a new rule that validates if any of the the combined rules validates
   */
  def or(that: ValidationRule[A]): ValidationRule[A] =
    ValidationRule(s"${self.description} or ${that.description}") { value => self(value) || that(value) }
}

/**
 * Allows the creation of reusable validation rules for [[RequestReader]]s.
 */
object ValidationRule {

  /**
   * Creates a new reusable validation rule based on the specified predicate.
   *
   * @param desc text describing the rule being validated
   * @param p returns true if the data is valid
   *
   * @return a new reusable validation rule.
   */
  def apply[A](desc: String)(p: A => Boolean): ValidationRule[A] = new ValidationRule[A] {
    def description = desc
    def apply(value: A) = p(value)
  }
}
