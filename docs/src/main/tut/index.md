---
layout: home
title:  "Home"
section: "home"
technologies:
 - first: ["Scala", "sbt-microsites plugin is completely written in Scala"]
 - second: ["SBT", "sbt-microsites plugin uses SBT and other sbt plugins to generate microsites easily"]
 - third: ["Jekyll", "Jekyll allows for the transformation of plain text into static websites and blogs."]
---

Finch provides a combinator API over the [Finagle][finagle] HTTP services. An `Endpoint[A]`, main
abstraction for which combinators are defined, represents an HTTP endpoint that takes a request and
returns a value of type `A`.

### Look and Feel

The following example creates an HTTP server (powered by Finagle) that serves the only endpoint
`POST /time`. This endpoint takes a `Locale` instance represented as JSON in request _body_ and
returns a current `Time` for this locale.

build.sbt:

```scala
libraryDependencies ++= Seq(
  "com.github.finagle" %% "finch-core" % "0.27.0",
  "com.github.finagle" %% "finch-circe" % "0.27.0",
  "io.circe" %% "circe-generic" % "0.9.3"
)
```

Main.scala:

```tut:silent
import com.twitter.finagle.Http
import com.twitter.util.Await

import io.finch._
import io.finch.circe._
import io.finch.syntax._
import io.circe.generic.auto._

object Main extends App {

  case class Locale(language: String, country: String)
  case class Time(locale: Locale, time: String)

  def currentTime(l: java.util.Locale): String =
    java.util.Calendar.getInstance(l).getTime.toString

  val time: Endpoint[Time] =
    post("time" :: jsonBody[Locale]) { l: Locale =>
      Ok(Time(l, currentTime(new java.util.Locale(l.language, l.country))))
    }

  Await.ready(Http.server.serve(":8081", time.toService))
}
```

### What People Say?

[@mandubian](https://twitter.com/mandubian) on
[Twitter](https://twitter.com/mandubian/status/652136674353283072):

> I think there is clearly room for great improvements using pure FP in Scala for HTTP API & #Finch
> is clearly a good candidate.

[@tperrigo](https://www.reddit.com/user/tperrigo) on
[Reddit](https://www.reddit.com/r/scala/comments/3kaael/which_framework_to_use_for_development_of_a_rest/cv13vvg):

> I'm currently working on a project using Finch (with [Circe][circe] to serialize my case classes
> to JSON without any  boilerplate code -- in fact, besides the import statements, I don't have to
> do anything to transform my results to JSON) and am extremely impressed. There are still a few
> things in flux with Finch, but I'd recommend giving it a look.

[@arnarthor](https://github.com/arnarthor) on
[Gitter](https://gitter.im/finagle/finch?at=56159d7476d984a35875c13a):

> I am currently re-writing a NodeJS service in Finch and the code is so much cleaner and readable
> and about two thirds the amount of lines. Really love this.

### Finch Talks

* [Put Some[Types] on your **HTTP** endpoints][matsuri17] by [@vkostyukov][vkostyukov] in Feb 17
* [Functional Microservices with Finch and Circe][ucon16] by [@davegurnell][davegurnell] in Nov 16
* [Typed Services Using Finch][ylj16] by [@tomjadams][tomjadams] in Apr 2016
* [Finch: Your REST API as a Monad][scalax] by [@vkostyukov][vkostyukov] in Dec 2015
* [On the history of Finch][sfscala-vk] by [@vkostyukov][vkostyukov] in Apr 2015
* [Some possible features for Finch][sfscala-tb] [@travisbrown][travisbrown] in Apr 2015


[finagle]: http://twitter.github.io/finagle/
[circe]: https://github.com/travisbrown/circe
[matsuri17]: http://kostyukov.net/slides/finch-tokyo
[ylj16]: https://www.youtube.com/watch?v=xkZOyY9PG88
[ucon16]: https://skillsmatter.com/skillscasts/9335-high-flying-free-and-easy-functional-microservices-with-finch
[scalax]: https://skillsmatter.com/skillscasts/6876-finch-your-rest-api-as-a-monad
[sfscala-vk]: https://www.youtube.com/watch?v=bbzRTxGDFhs
[sfscala-tb]: https://www.youtube.com/watch?v=noCyZ6B__iE
[vkostyukov]: https://twitter.com/vkostyukov
[travisbrown]: https://twitter.com/travisbrown
[tomjadams]: https://twitter.com/tomjadams
[davegurnell]: https://twitter.com/davegurnell