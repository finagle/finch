## Requests

* [Request Reader](request.md#request-reader)
  * [Overview](request.md#overview)
  * [API](request.md#api)
* [Base Readers](request.md#base-readers)
  * [Required and Optional Readers](request.md#required-and-optional-readers)
  * [Multi-Value Parameters](request.md#multi-value-parameters)
  * [Custom Readers](request.md#custom-readers)
  * [Error Handling](request.md#error-handling)
* [Combining and Reusing Readers](request.md#combining-and-reusing-readers)
  * [Applicative Syntax](request.md#applicative-syntax)
  * [Monadic Syntax](request.md#monadic-syntax)
* [Type Conversion](request.md#type-conversion)
  * [Built-in Decoders](request.md#built-in-decoders)
  * [Custom Decoders](request.md#custom-decoders)
  * [Integration with JSON Libraries](request.md#integration-with-json-libraries)
* [Validation](request.md#validation)
  * [Inline Validation](request.md#inline-validation)
  * [Reusable Rules](request.md#reusable-validators)
  * [Built-in Rules](request.md#built-in-rules)
* [A Note about Custom Request Types](request.md#a-note-about-custom-request-types)

--

### Request Reader

Finch's `RequestReader` is an implementation of the [reader monad][2], a common design pattern in functional programming.
A `RequestReader[A]` is just a wrapper for a function `Request => Future[A]` that provides `map` and `flatMap`
implementations, together with a few other combinators.

The purpose of the reader monad is to avoid repetitive boilerplate when composing operations that read from a common
environment of some kind. For example, we might find ourselves writing something like this when processing an HTTP
request in a Finagle application:

```scala
import io.finch._
import com.twitter.finagle.http.{Request, Response}

def param(req: Request)(key: String): Option[String] =
  req.params.get(key) orElse {
    ??? // try to get parameter from multipart form
  }

def doSomethingWithRequest(req: Request): Option[Result] =
  for {
    foo <- param(req)("foo")
    bar <- param(req)("bar")
    baz <- req.headerMap.get("baz")
    qux <- req.headerMap.get("qux")
    content <- Some(req.contentString)
  } yield Result(...)
```

This works, but it's often useful to be able to make each of these _reading_ operations independent and composable pieces
(not expressions built around a `req` variable that happens to be in scope). The reader monad makes this easy. For example,
we could rewrite our `doSomethingWithRequest` operation as follows using Finch's `RequestReader`:

```scala
val doSomethingWithRequest: RequestReader[Result] =
  for {
    foo <- param("foo")
    bar <- param("bar")
    baz <- header("baz")
    qux <- header("qux")
    content <- body
  } yield Result(...)
```

We could then "run" the request reader by passing it a `Request`:

```scala
val result: Future[Result] = doSomethingWithRequest(myReq)
```

What's happening here is that we're building up a large `Request => A` function out of smaller `Request => A`
pieces. `param("foo")`, `header("baz")` and `body`, for example, are all values of type `RequestReader[String]`, where
`param`, `header`, and `body` are generally useful readers that are provided by Finch.

Note that the result of running a request reader is a value in a future (not an `Option`, as in our original example).
This makes it possible to chain readers together with Finagle services in a single `for`-comprehension. This can be
extremely useful when a service should fetch and validate the request parameters before doing the real job, and not do
the job at all if the parameters are not valid. A request reader can just return a failed future and no further
operations in the `for`-comprehension will be performed.

#### Overview

A typical `RequestReader` might look like this:

```scala
import io.finch.request._

case class User(name: String, age: Int, city: String)

val user: RequestReader[User] = (
  param("name") ::
  param("age").as[Int].shouldNot(beLessThan(18)) ::
  paramOption("city").withDefault("Novosibirsk")
).as[User]
```

A `RequestReader` is responsible for the following typical tasks in request
processing:

* reading parameters, header, cookies or the body of the request (see [Base Readers](request.md#base-readers)).
* declaring these artifacts as either required or optional (see [Required and Optional Readers](request.md#required-and-optional-readers)).
* converting `String`-based and composite inputs to other types with the `as[A]` method (see [Type Conversion](request.md#type-conversion)).
* validating one or more readers with `should` or `shouldNot` (see [Validation](request.md#validation)).
* combining multiple readers with the `::` combinator method (see [Combining and Reusing Readers](request.md#combining-and-reusing-readers)).

#### API

The `RequestReader` API is fairly simple. It allows the user to apply the reader to a request instance with `apply`, to
transform the reader with `map` (or `~>`), to transform the reader in a `RequestReader` or `Future` context (`flatMap`
and `embedFlatMap` respectively), to combine it with other readers with the `::` combinator, and to validate it with
`should` or `shouldNot`:

```scala
trait RequestReader[A] {
  def apply(req: Request): Future[A]

  def map[B](fn: A => B): RequestReader[B]
  def flatMap[B](fn: A => RequestReader[B]): RequestReader[B]

  def ::[B](that: RequestReader[B]): RequestReader[A :: B :: HNil]

  def should(rule: String)(predicate: A => Boolean): RequestReader[A]
  def shouldNot(rule: String)(predicate: A => Boolean): RequestReader[A]
  def should(rule: ValidationRule[A]): RequestReader[A]
  def shouldNot(rule: ValidationRule[A]): RequestReader[A]
}
```

In addition there are implicit `as[A]` methods available for type conversion on `String`-based and composite
(`HList`-based) readers. See [Type Conversion](request.md#type-conversion) for more details.

```scala
// for all `RequestReader[String]`
def as[A](implicit decode: DecodeRequest[A], tag: ClassTag[A]): RequestReader[A]

// for all `RequestReader[Option[String]]`
def as[A](implicit decode: DecodeRequest[A], tag: ClassTag[A]): RequestReader[Option[A]]

// for all `RequestReader[Seq[String]]`
def as[A](implicit decode: DecodeRequest[A], tag: ClassTag[A]): RequestReader[Seq[A]]

// for all `RequestReader[L <: HList]`
def as[A](implicit gen: Generic.Aux[A, L]): RequestReader[A]
```

The following sections cover all these features in more detail. All sample code assumes that you have imported
`io.finch.request._`.

Finally, `RequestReader`s that return `Option` values have a couple of additional useful methods:
`withDefault(value: A)` and `orElse(alternative: Option[A])`.

### Base Readers

Finch provides a set of base readers for extracting parameters, headers, cookies or the body from the request. The
column for the result type specifies the type parameter of the resulting reader (e.g. `Option[String]` means the reader
is a `RequestReader[Option[String]]`).

Request Item          | Reader Type                          | Result Type
----------------------| -------------------------------------| ----------------------------------
Parameter             | `param(name)`/`paramOption(name)`    | `String`/`Option[String]`
Multi-Value Parameters| `paramsNonEmpty(name)`/`params(name)`| `Seq[String]`/`Seq[String]`
Header                | `header(name)`/`headerOption(name)`  | `String`/`Option[String]`
Cookie                | `cookie(name)`/`cookieOption(name)`  | `Cookie`/`Option[Cookie]`
Text Body             | `body`/`bodyOption`                  | `String`/`Option[String]`
Binary Body           | `binaryBody`/`binaryBodyOption`      | `Array[Byte]`/`Option[Array[Byte]]`
File Upload           | `fileUpload`/`fileUploadOption`      | `FileUpload`/`Option[FileUpload]`

#### Required and Optional Readers

As you can see in the table above, the six base readers all come in two flavors, allowing one to declare a request item
as either required or optional.

* An `x` reader fails with a `NotPresent` exception if the item is not found in the request
* An `xOption` reader always succeeds, producing a `None` if the item is not found in the request
* If you apply type conversions or validations to an optional item, the behaviour is as follows:
  * If the result is `None`, all type conversions and validations are skipped and the reader succeeds with a `None` result
  * If the result is non-empty, all type conversions and validations have to succeed or otherwise the reader will fail

#### Multi-Value Parameters

The `paramsNonEmpty` and `params` readers read multi-value parameters in the following way:

* In case of multiple occurrences of the same parameter in the URL, the values are combined into a single `Seq[String]`
* If any of the values is a comma-separated list, it will be split into `Seq[String]`

Thus, the following HTTP params `a=1,2,3&b=4&b=5` might be fetched with the `paramsNonEmpty` reader like this:

```scala
// asTuple method is available on HList-based readers
val reader: RequestReader[(Seq[Int], Seq[Int])] = (
  paramsNonEmpty("a").as[Int] ::
  paramsNonEmpty("b").as[Int]
).asTuple

val (a, b): (Seq[Int], Seq[Int]) = reader(request)
// a = Seq(1, 2, 3)
// b = Seq(4, 5)
```

#### Custom Readers

In most cases you will combine several of the built-in base readers to compose new readers. For the rare cases where you
want to create a new reader type yourself, the `RequestReader` companion object comes with a range of convenient factory
methods:

```scala
// Creates a new reader that always succeeds, producing the specified value.
def value[A](value: A): RequestReader[A]

// Creates a new reader that always fails, producing the specified exception.
def exception[A](exc: Throwable): RequestReader[A]

// Creates a new reader that always produces the specified value.
def const[A](value: Future[A]): RequestReader[A]

// Creates a new reader that reads the result from the request.
def apply[A](f: Request => A): RequestReader[A]
```

#### Error Handling

The exceptions from a request reader might be handled just like other failed futures in Finagle:

```scala
val user: Future[Json] = service(...) handle {
  case NotFound(ParamItem(param)) =>
    Json.obj("error" -> "param_not_found", "param" -> param)
  case NotValid(ParamItem(param), rule) =>
    Json.obj("error" -> "validation_failed", "param" -> param, "rule" -> rule)
}
```

All the exceptions thrown by `RequestReader` are case classes. Therefore pattern matching may be used to handle them.

These are all error types produced by Finch (note that all extend `RequestError`):

```scala
 // when multiple request items were invalid or missing
case class RequestErrors(errors: Seq[Throwable])

// when a required request item (header, param, cookie, body) was missing
case class NotFound(item: RequestItem)

// when type conversion failed
case class NotParsed(item: RequestItem, targetType: ClassTag[_], cause: Throwable)

// when a validation rule did not pass for a request item
case class NotValid(item: RequestItem, rule: String)
```

The `RequestItem` is a following ADT:

```scala
sealed abstract class RequestItem(val kind: String, val nameOption:Option[String] = None) {
  val description = kind + nameOption.fold("")(" '" + _ + "'")
}
case class ParamItem(name: String) extends RequestItem("param", Some(name))
case class HeaderItem(name: String) extends RequestItem("header", Some(name))
case class CookieItem(name: String) extends RequestItem("cookie", Some(name))
case object BodyItem extends RequestItem("body")
case object MultipleItems extends RequestItem("request")
```

### Combining and Reusing Readers

As you have already seen in previous example, Finch provides the basic building blocks for request processing in the
form of readers for parameters, headers, cookies and the request body.

You then perform type conversions or validations on these readers as required and combine them to build new readers:

```scala
case class Address(street: String, city: String, postCode: String)

val address: RequestReader[Address] = (
  param("street") ::
  param("city") ::
  param("postCode").shouldNot(beLongerThan(5))
).as[Address]
```

These new readers can then themselves be combined with other readers:

```scala
case class User(name: String, address: Address)

val user: RequestReader[User] =
  (param("name") :: address).as[User]
```

The example above may be rewritten with `map` over the `HList` and pattern-matching:

```scala
case class User(name: String, address: Address)

val user: RequestReader[User] = (
  (param("name") :: address).map {
    case name :: address :: HNil  => User(name, address)
  }
```

The following sections explain the difference between the applicative style (`HList` style) based on the `::` combinator
you see in the examples above and the monadic style that you will only need in exceptional cases.

#### Applicative Syntax

Almost all the examples in this documentation show the applicative syntax based on the `::` combinator for composing
readers. It is similar to [scodec's][3] `::` compositor.

```scala
case class User(name: String, age: Int)

val user: RequestReader[User] = (
  param("name") ::
  param("age").as[Int]
).as[User]
```

The `::` operator composes two request readers into a `RequestReader[L <: HList]`, where the `HList` type is provided by
[Shapeless][4].

The main advantage of this style is that errors will be collected. If the name parameter is missing and the age parameter
cannot be converted to an integer, both errors will be included in the failed future, in an exception class
`RequestErrors` that has an `errors` property of type `Seq[Throwable]`:

```scala
user(Request("age" -> "broken"))

// will return a `Future` failing with this exception:
RequestErrors(Seq(
  NotPresent(ParamItem("name")),
  NotParsed(ParamItem("age"), <ClassTag[Int]>, <NumberFormatException>)
))
```

#### Monadic Syntax

Since the `RequestReader` is a reader monad, you can alternatively combine readers in `for`-comprehensions (using `map`
and `flatMap`):

```scala
case class User(name: String, age: Int)

val user: RequestReader[User] = for {
  name <- param("name")
  age <- param("age").as[Int]
} yield User(name, age)
```

But while this syntax may look familiar and intuitive, it has the major disadvantage that it is fail-fast. If both
parameters are invalid, only one error will be returned—a fact your users and client developers probably won't fancy
much.

The monadic style might still be useful for the rare cases where one reader depends on the result of another reader.

Note: If you've used older versions of Finch (before 0.5.0), then the monadic style was the only way to combine readers.
The applicative style has been introduced in version 0.5.0 and is the recommended combinator pattern now.

### Type Conversion

For all `String`-based readers, Finch provides an `as[A]` method to perform type conversions. It is available for any
`RequestReader[String]`, `RequestReader[Option[String]]` or `RequestReader[Seq[String]]` as long as a matching implicit
`DecodeRequest[A]` type-class is in scope.

This facility is designed to be intuitive, meaning that you do not have to provide a `DecodeRequest[Seq[MyType]]` for
converting a sequence. A decoder for a single item will allow you to convert `Option[String]` and `Seq[String]`, too:

```scala
param("foo").as[Int]        // RequestReader[Int]
paramOption("foo").as[Int]  // RequestReader[Option[Int]]
params("foo").as[Int]       // RequestReader[Seq[Int]]
```

The same method `as[A]` is also available on any `RequestReader[L <: HList]` to perform [Shapeless][4]-powered generic
conversions from `HList`s to case classes with appropriately typed members.

```scala
case class Foo(i: Int, s: String)

val hlist: RequestReader[Int :: String :: HNil] =
  param("i").as[Int] :: param("s") // uses Finch's DecodeRequest to convert String to Int

val user: RequestReader[User] =
  hlist.as[User] // uses Shapeless' Generic.Aux to convert HList to User
```

Note that while both methods take different implicit params and use different techniques to perform type-conversions,
they're basically doing the same thing: transforming the underlying type `A` into some type `B` (that's why they have
similar names.

#### Built-in Decoders

Finch comes with predefined decoders for `Int`, `Long`, `Float`, `Double` and `Boolean`. As long as you have imported
`io.finch.request._` the implicits for these decoders are in scope and can be used with the `as[A]` method:

```scala
val reader: RequestReader[Int] = param("foo").as[Int]
```

[Shapeless][4] supplies `Generic.Aux` instances for any case class, so `as[A]` may also be used to convert an underlying
`HList` into any case class if their arity and types are the same.

#### Custom Decoders

Writing a new decoder for a type not supported out of the box is very easy, too. The following example shows a decoder
for a Joda `DateTime` from a `Long` representing the number of milliseconds since the epoch:

```scala
implicit val dateTimeDecoder: DecodeRequest[DateTime] =
  DecodeRequest(s => Try(new DateTime(s.toLong)))
```

The example shows the most concise way of creating a new decoder: using the factory method on the companion object of
`DecodeRequest`:

```scala
def apply[A](f: String => Try[A]): DecodeRequest[A]
```

All you need to implement is a simple function from `String` to `Try[A]`.

As long as the implicit declared above is in scope, you can then use your custom decoder in the same way as any of the
built-in decoders (in this case for creating a JodaTime `Interval`:

```scala
val interval: RequestReader[Interval] = (
  param("start").as[DateTime] ::
  param("end").as[DateTime]
).as[Interval]
```

#### Integration with JSON Libraries

A third way of using the `as[A]` type conversion facility is to use one of the JSON library integrations Finch offers.
Finch comes with support for [Argonaut](json.md#argonaut), [Jackson](json.md#jackson) and [JSON4S](json.md#json4s).

All these integration modules do is make the library-specific JSON decoders available for use as a `DecodeRequest[A]`.
To take Argonaut as an example, you only have to import `io.finch.argonaut._` to have implicit Argonaut
`DecodeJSON` instances in scope:

```scala
case class Person(name: String, age: Int)

implicit def PersonDecodeJson: DecodeJson[Person] =
  jdecode2L(Person.apply)("name", "age")
```

Finch will automatically adapt these implicits to its own `DecodeRequest[Person]` type,  so that you can use the `as[A]`
method on a reader for a body sent in JSON format:

```scala
val person: RequestReader[Person] = body.as[Person]
```

The integration for the other JSON libraries works in a similar way.

### Validation

The `should` and `shouldNot` methods on `RequestReader` allow the user to perform validation logic. If the specified
predicate does not hold, the reader will fail with a `NotValid(item, rule)` exception. The `rule` is a description that
you pass to the `should` or `shouldNot` methods as a string.

Note that for an optional reader, the validation will be skipped for `None` results, but if the value is non-empty then
all validation must succeed for the reader to succeed.

Validation can happen inline or based on predefined validation rules, as shown in the next two sections.

#### Inline Validation

For validation logic only needed in one place, the most convenient way is to declare it inline:

```scala
val adult2: RequestReader[User] = (
  param("name") ::
  param("age").as[Int].shouldNot("be less than 18") { _ < 18 }
).as[User]
```

#### Reusable Rules

If you perform the same validation logic in multiple readers, it is more convenient to declare them separately and reuse
them wherever needed:

```scala
val bePositive = ValidationRule[Int]("be positive") { _ > 0 }
def beLessThan(value: Int) = ValidationRule[Int](s"be less than $value") { _ < value }

val child: RequestReader[User] = (
  param("name") ::
  param("age").as[Int].should(bePositive and beLessThan(18))
).as[User]
```

As you can see in the example above, predefined rules can also be logically combined with `and` or `or`.

#### Built-in Rules

Finch comes with a small set of predefined rules. For readers producing numeric results, you can use `beLessThan(n: Int)`
or `beGreaterThan(n: Int)`, and for strings you can use `beLongerThan(n: Int)` or `beShorterThan(n: Int)`.

### A Note about Custom Request Types

**Important:** Custom request types are supported via the `PRequestReader` type (which `RequestReader` extends), but are
not generally recommended, since the don't fit well into Finch's philosophy, which is based on the concepts of functional
programming (programming with functions). Finch's idiomatic style is built on the idea that ["your server is a function"][0]
and promotes using simple functions `Request => A` (i.e., `RequestReader`s) instead of overriding the request types.

With that said, a custom request types **are deprecated since 0.8.0**.

A common pattern (now discouraged in Finch) is to implement authorization using Finagle filters and custom request types
(i.e. an `AuthRequest`). In Finch, the same effect may be achieved using `RequestReader[AuthorizedUser]` composed in
every endpoint that requires information about current user. Custom request types will likely be deprecated in favour of
`RequestReader`s in 0.8.0.

--
Read Next: [Responses](response.md)

[0]: http://monkey.org/~marius/funsrv.pdf
[1]: https://github.com/finagle/finch/blob/master/demo/src/main/scala/io/finch/demo/Main.scala
[2]: http://www.haskell.org/haskellwiki/All_About_Monads#The_Reader_monad
[3]: https://github.com/scodec/scodec
[4]: https://github.com/milessabin/shapeless
