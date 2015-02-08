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

import scala.reflect.ClassTag
import io.finch.request.items._

/**
 * A base exception of request reader.
 *
 * @param message the message
 */
class RequestError(val message: String, cause: Throwable) extends Exception(message, cause) {
  def this(message: String) = this(message, null)
}

/**
 * An exception that collects multiple request reader errors.
 *
 * @param errors the errors collected from various request readers
 */
case class RequestErrors(errors: Seq[Throwable])
  extends RequestError("One or more errors reading request: " + errors.map(_.getMessage).mkString("\n  ","\n  ",""))

/**
 * An exception that indicates a required request item (header, param, cookie, body)
 * was missing in the request.
 *
 * @param item the missing request item
 */
case class NotPresent(item: RequestItem) extends RequestError(s"Required ${item.description} not present in the request.")

/**
 * An exception that indicates a broken validation rule on the request item.
 *
 * @param item the invalid request item
 * @param rule the rule description
 */
case class NotValid(item: RequestItem, rule: String)
  extends RequestError(s"Validation failed: ${item.description} $rule.")

/**
 * An exception that indicates that a request item could be parsed.
 *
 * @param item the invalid request item
 * @param targetType the type the item should be converted into
 * @param cause the cause of the parsing error
 */
case class NotParsed(item: RequestItem, targetType: ClassTag[_], cause: Throwable)
  extends RequestError(s"${item.description} cannot be converted to ${targetType.runtimeClass.getSimpleName}: ${cause.getMessage}.")

