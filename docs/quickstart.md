## Quickstart

In this quick start example we will build a simple HTTP service that greets a user by given `name` and `title`. We will
use Scala REPL to run all the code in the interactive mode. Assuming that [SBT is installed][1], and the following
`build.sbt` file exists in the current directory, you can run REPL with `sbt console`.

```scala
name := "finch-quickstart"

version := "0.0.0"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  "com.github.finagle" %% "finch-core" % "0.5.0"
)
```

First things first. In order to import Finch core classes run the following code in the REPL.

```scala
import io.finch._
import io.finch.request._
import io.finch.response._
import io.finch.route._
```

In addition to the Finch API we will also need a couple of Finagle basic blocks, such as `Service` and `Httpx`.

```scala
import com.twitter.finagle.Service
import com.twitter.finagle.httpx.Httpx
```

Using the [route combinators](route.md), we may define a _route_ for our `hello` service. In the code bellow `endpoint`
accepts GET request with path `/(hello|hi)/:name` and routes them to the underling service.

```scala
val endpoint = Get / ("hello" | "hi") / string /> hello
```

In order to _read_ the incoming request and fetch the query-string param `title`, we will use the
[request reader](request.md). The defined `RequestReader` reads optional param `title` with an empty string as default
value.

```scala
val title = paramOption("title").withDefault("")
```

Finally, we define a service `hello` that actually greets users. The HTTP response `OK 200` is _built_ with
[response builder](response.md) `Ok` that takes a string and returns a `plain/text` HTTP response.  

```scala
def hello(name: String) = new Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest) = for {
    t <- title(req)
  } yield Ok(s"Hello, $t $name!")
}
```

The following code serves the given endpoint with Finagle's Httpx codec.

```scala
Await.ready(Httpx.serve(":8081", endpoint))
```

So, we can now query the backend with `curl`.

```
curl localhost:8081/hello/Bob?title=Mr.
curl localhost:8081/hi/Alice
```

--
Read Next: [Demo](demo.md)

[1]: http://www.scala-sbt.org/0.13/tutorial/Setup.html