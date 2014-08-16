Request Reader Monad
--------------------
**Finch.io** has built-in request reader that implement the [Reader Monad](http://www.haskell.org/haskellwiki/All_About_Monads#The_Reader_monad) functional design pattern: 
* `io.finch.request.RequestReader` reading `Future[A]`

A `RequestReader` has return type `Future[A]` so it might be simply used as an additional monad-transformation in a top-level for-comprehension statement. This is dramatically useful when a service should fetch some params from a request before doing a real job (and not doing it at all if some of the params are not found/not valid).

The following readers are available in Finch.io:
* `io.finch.request.EmptyReader` - throws an exception instead of reading 
* `io.finch.request.ConstReader` - fetches a const value from the request
* `io.finch.request.RequiredParam` - fetches required params within specified type
* `io.finch.request.OptionalParam` - fetches optional params within specified type
* `io.finch.request.RequiredParams` - fetches required multi-value params into the list
* `io.finch.request.OptionalParams` - fetches optional multi-value params into the list
* `io.finch.request.ValidationRule` - fails if given predicate is false

```scala
case class User(name: String, age: Int, city: String)

// Define a new request reader composed from provided out-of-the-box readers.
val user = for {
  name <- RequiredParam("name")
  age <- RequiredIntParam("age")
  city <- OptionalParam("city")
} yield User(name, age, city.getOrElse("Novosibirsk"))

val service = new Service[HttpRequest, JsonResponse] {
  def apply(req: HttpRequest) = for {
    u <- user(req)
  } yield JsonObject(
    "name" -> u.name, 
    "age" -> u.age, 
    "city" -> u.city
  )
}

val user = service(...) handle {
  case e: ParamNotFound => JsonObject("status" -> 400) // bad request
}
```

The most cool thing about monads is that they may be composed/reused as hell. Here is an example of _extending_ an existing reader with new fields/validation rules.

```scala
val restrictedUser = for {
  u <- user
  _ <- ValidationRule("age", "should be greater then 18") { user.age > 18 }
} yield user
```

The exceptions from a request-reader might be handled just like other future exceptions in Finagle:
```scala
val user = service(...) handle {
  case e: ParamNotFound => JsonObject("status" -> 400, "error" -> e.getMessage, "param" -> e.param)
  case e: ValidationFailed => JsonObject("status" -> 400, "error" -> e.getMessage, "param" -> e.param)
}
```

Optional params are quite often used for fetching pagination details.
```scala
val pagination = for {
  offset <- OptionalIntParam("offset")
  limit <- OptionalIntParam("limit")
} yield (offsetId.getOrElse(0), math.min(limit.getOrElse(50), 50))

val service = new Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest) = for {
    (offsetIt, limit) <- pagination(req)
  } yield Ok(s"Fetching items $offset..${offset+limit}")
}
```
#### A `io.finch.requests.RequiredParam` reader makes sure that
* param is presented in the request (otherwise it throws `ParamNoFound` exception)
* param is not empty (otherwise it throws `ValidationFailed` exception)
* param may be converted to a requested type `RequiredIntParam`, `RequiredLongParam` or `RequiredBooleanParam` (otherwise it throws `ValidationFailed` exception).

#### An `io.finch.request.OptionalParam` returns 
* `Future[Some[A]]` if param is presented in the request and may be converted to a requested type `OptionalIntParam`, `OptionalLongParam` or `OptionalBooleanParam`
* `Future.None` otherwise.

#### A `io.finch.request.ValidationRule(param, rule)(predicate)` 
* returns `Future.Done` when predicate is `true`
* throws `ValidationFailed` exception with `rule` and `param` fields

### Multiple-Value Params
All the readers have companion readers that can read multiple-value params `List[A]` instead of single-value params `A`. Multiple-value readers have `s` postfix in their names. So, `Param` has `Params`, `OptionalParam` has `OptipnalParams` and finally `RequiredParam` has `RequiredParams` companions. There are also typed versions for every reader, like `IntParams` or even `OptionalLongParams`.

Thus, the following HTTP params `a=1,2,3&b=4&b=5` might be fetched with `RequiredIntParams` reader like this:

```scala
val reader = for {
 a <- RequiredIntParams("a")
 b <- RequiredIntParams("b")
} yield (a, b)

val (a, b): (List[Int], List[Int]) = reader(request)
// a = List(1, 2, 3)
// b = List(4, 5)
```

### HTTP Headers
The HTTP headers may also be read with `RequestReader`. The following pre-defined readers should be used:
* `io.finch.request.RequiredHeader` - fetches header or throws `HeaderNotFound` exception
* `io.finch.request.OptionalHeader` - fetches header into an `Option`

###### Read Next: [Responses](response.md)