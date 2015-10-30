## Quickstart

In this quick start example we will build a simple HTTP service that greets a user by given `name` and `title`. We will
use Scala REPL to run all the code in the interactive mode. Assuming that [SBT is installed][1], and the following
`build.sbt` file exists in the current directory, you can run REPL with `sbt console`.

```scala
name := "finch-quickstart"

version := "0.0.0"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.github.finagle" %% "finch-core" % "0.8.0"
)
```

Let's build a service that greets the user by given `name` and `title`.

In order to _read_ the incoming request and fetch the query-string param `title`, we will use the
[request reader](request.md). The defined `RequestReader` reads optional param `title` with an empty string as default
value.

```scala
val title: RequestReader[String] = paramOption("title").withDefault("")
```

Using the [route combinators](route.md), we may define a _router_ `hello`.

```scala
val api: Router[String] =
  get(("hello" | "hi") / string ? title) { (name: String, title: String) =>
    s"Hello, $title $name!"
  }
```

The following code serves the given API endpoint with Finagle's Http codec.

```scala
Await.ready(Http.serve(":8081", api.toService))
```

So, we can now query the backend with `curl`.

```
curl localhost:8081/hello/Bob?title=Mr.
curl localhost:8081/hi/Alice
```

--
Read Next: [Demo](demo.md)

[1]: http://www.scala-sbt.org/0.13/tutorial/Setup.html
