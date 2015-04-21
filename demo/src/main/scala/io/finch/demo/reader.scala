/*
 * Copyright 2015, by Vladimir Kostyukov and Contributors.
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
 * Contributor(s): -
 */

package io.finch.demo

import argonaut._, Argonaut._
import io.finch.request._
import io.finch.argonaut._

object reader {

  import model._

  // A requests reader that reads user objects from the http request.
  // A user is represented by url-encoded param `name`.
  val user: RequestReader[User] =
    param("name") should beLongerThan(5) map { name =>
      User(Id(), name, List.empty[Ticket])
    }

  // A request reader that reads ticket object from the http request.
  // A ticket is represented by a JSON object serialized in request body.
  val ticket: RequestReader[Ticket] =
    body.as[Json] map { json =>
      Ticket(Id(), (json.hcursor -- "label").as[String].getOr("N/A"))
    }
}
