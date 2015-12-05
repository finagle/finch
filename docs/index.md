<p align="center">
  <img src="https://raw.githubusercontent.com/finagle/finch/master/finch-logo.png" width="360px" />
</p>

Finch is a simple library that provides an immutable layer of functions and types on top of [Finagle][finagle] for
writing lightweight and composable HTTP services in a functional settings.

### Look and Feel

The following example creates an HTTP server (powered by Finagle) that serves the only endpoint `POST /time`. This
endpoint takes a `Locale` instance represented as JSON in request _body_ and returns a current `Time` for this locale.

build.sbt:

```scala
libraryDependencies ++= Seq(
  "com.github.finagle" %% "finch-core" % "0.9.1",
  "com.github.finagle" %% "finch-circe" % "0.9.1",
  "io.circe" %% "circe-generic" % "0.2.0"
)
```

Main.scala:

```scala
import com.twitter.finagle.Http
import com.twitter.util.Await

import io.finch._
import io.finch.request._
import io.finch.circe._
import io.circe.generic.auto._

object Main extends App {

  case class Locale(language: String, country: String)
  case class Time(locale: Locale, time: String)

  def currentTime(l: java.util.Locale): String =
    java.util.Calendar.getInstance(l).getTime.toString

  val time: Endpoint[Time] =
    post("time" ? body.as[Locale]) { l: Locale =>
      Ok(Time(l, currentTime(new java.util.Locale(l.language, l.country))))
    }

  Await.ready(Http.server.serve(":8081", time.toService))
}
```

### What People Say?

[@mandubian](https://twitter.com/mandubian) on [Twitter](https://twitter.com/mandubian/status/652136674353283072):

> I think there is clearly room for great improvements using pure FP in Scala for HTTP API & #Finch is clearly a
> good candidate.

[@tperrigo](https://www.reddit.com/user/tperrigo) on
[Reddit](https://www.reddit.com/r/scala/comments/3kaael/which_framework_to_use_for_development_of_a_rest/cv13vvg):

> I'm currently working on a project using Finch (with [Circe][circe] to serialize my case classes to JSON without any
> boilerplate code-- in fact, besides the import statements, I don't have to do anything to transform my results to
> JSON) and am extremely impressed. There are still a few things in flux with Finch, but I'd recommend giving it a look.

[@arnarthor](https://github.com/arnarthor) on [Gitter](https://gitter.im/finagle/finch?at=56159d7476d984a35875c13a):

> I am currently re-writing a NodeJS service in Finch and the code is so much cleaner and readable and about two thirds
> the amount of lines. Really love this.

### Finch Talks/Slides

* [An introduction to Finch at Scala BASE](https://rpless.github.io/finch-intro/) by
  [@ryan_plessner](https://twitter.com/ryan_plessner) on Sep 2015
* [Finch Workshop at FinagleCon](http://vkostyukov.ru/slides/finch-101/) by [@vkostyukov](https://twitter.com/vkostyukov)
  on Aug 2015
* [Finch and Shapeless at FinagleCon](https://meta.plasm.us/slides/finagle/finch-and-shapeless/) by
  [@travisbrown](https://twitter.com/travisbrown) on Aug 2015

# User Guide

* [Endpoints](endpoint.md)
* [RequestReaders](request.md)
* [Authentication](auth.md)
* [JSON](json.md)
* [Best Practices](best-practices.md)

[finagle]: http://twitter.github.io/finagle/
[circe]: https://github.com/travisbrown/circe
