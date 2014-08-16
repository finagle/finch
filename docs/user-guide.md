A Hacker's Guide to Purely Functional API
-----------------------------------------

**Step 1:** Define a model (optional):
```scala
case class User(id: Long, name: String)
case class Ticket(id: Long)
```

**Step 2:** Implement REST services:

```scala
import io.finch._
import com.twitter.finagle.Service

case class GetUser(userId: Long) extends Service[HttpRequest, User] {
  def apply(req: HttpRequest) = User(userId, "John").toFuture
}

case class GetTicket(ticketId: Long) extends Service[HttpRequest, Ticket] {
  def apply(req: HttpRequest) = Ticket(ticketId).toFuture
}

case class GetUserTickets(userId: Long) extends Service[HttpRequest, Seq[Ticket]] {
  def apply(req: HttpRequest) = Seq(Ticket(1), Ticket(2), Ticket(3)).toFuture
}
```

**Step 3:** Define filters/services for data transformation (optional):
```scala
import io.finch._
import io.finch.json._

object TurnModelIntoJson extends Service[Any, JsonResponse] {
  def apply(req: Any) = {
    def turn(any: Any): JsonResponse = any match {
      case User(id, name) => JsonObject("id" -> id, "name" -> name)
      case Ticket(id) => JsonObject("id" -> id)
      case seq: Seq[Any] => JsonArray(seq map turn :_*)
    }

    turn(req).toFuture
  }
}
```

**Step 4:** Define endpoints using services/filters for data transformation:
```scala
import io.finch._
import com.twitter.finagle.http.Method
import com.twitter.finagle.http.path._

object User extends Endpoint[HttpRequest, JsonResponse] {
  def route = {
    case Method.Get -> Root / "users" / Long(id) => 
      GetUser(id) ! TurnModelIntoJson
  }
}

object Ticket extends Endpoint[HttpRequest, JsonResponse] {
  def route = {
    case Method.Get -> Root / "tickets" / Long(id) =>
      GetTicket(id) ! TurnModelIntoJson
    case Method.Get -> Root / "users" / Long(id) / "tickets" =>
      GetUserTickets(id) ! TurnModelIntoJson
  }
}
```

**Step 5:** Expose endpoints:

```scala
import io.finch._
import io.finch.json._
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.{Http, RichHttp}
import java.net.InetSocketAddress

object Main extends App {
  val endpoint = Endpoint.join(User, Ticket) ! TurnJsonIntoHttp
  val backend = BasicallyAuthorize("user", "password") ! (endpoint orElse Endpoint.NotFound)

  ServerBuilder()
    .codec(RichHttp[HttpRequest](Http()))
    .bindTo(new InetSocketAddress(8080))
    .name("user-and-ticket")
    .build(backend.toService)
}
```

###### Read Next: [Endpoints](endpoint.md)