<p align="center">
  <img src="https://raw.githubusercontent.com/finagle/finch/master/finch-logo.png" width="500px" />
</p>

Finch is a thin layer of purely functional basic blocks atop of [Finagle][finagle] for
building composable HTTP APIs. Its mission is to provide the developers simple and robust HTTP primitives being as
close as possible to the bare metal Finagle API.

Badges
------
[![Build Status](https://img.shields.io/travis/finagle/finch/master.svg)](https://travis-ci.org/finagle/finch)
[![Coverage Status](https://img.shields.io/codecov/c/github/finagle/finch/master.svg)](https://codecov.io/github/finagle/finch)
[![Gitter](https://img.shields.io/badge/gitter-join%20chat-green.svg)](https://gitter.im/finagle/finch?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.finagle/finch-core_2.11.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.finagle/finch-core_2.11)

Standard Modules
----------------
Finch uses multi-project structure and contains of the following _modules_:

* [`finch-core`](core) - core classes/functions
* [`finch-generic`](generic) - generic derivation for endpoints
* [`finch-argonaut`](argonaut) - [Argonaut][argonaut] + Finch
* [`finch-circe`](circe) - [Circe][circe] + Finch
* [`finch-iteratee`](iteratee) - [Iteratee][iteratee] + Finch
* [`finch-refined`](refined) - [Refined][refined] + Finch
* [`finch-test`](test) - the test support classes/functions
* [`finch-sse`](sse) - SSE ([Server Sent Events][server-sent-events]) support in Finch

Additional Modules
------------------

Finch's Github organization has even more modules (these are, however, not published regularly;
reach out if you need published artifacts):

* [`finch-jackson`][finch-jackson] - [Jackson][jackson] + Finch
* [`finch-json4s`][finch-json4s] - [JSON4s][json4s] + Finch
* [`finch-playjson`][finch-playjson] - [PlayJson][playjson] + Finch
* [`finch-sprayjson`][finch-sprayjson] - [SprayJson][sprayjson] + Finch
* [`finch-oauth2`](finch-oath2) - [Finagle OAuth2](finagle-oauth2) + Finch

Installation
------------
Every Finch module is published at Maven Central. Use the following _sbt_ snippet ...

```scala
libraryDependencies ++= Seq(
  "com.github.finagle" %% "[finch-module]" % "[version]"
)
```

Hello World!
------------
This "Hello World!" example is built with just `finch-core`.

```scala
import io.finch._, cats.effect.IO
import com.twitter.finagle.Http

object Main extends App with Endpoint.Module[IO] {
  val api: Endpoint[IO, String] = get("hello") { Ok("Hello, World!") }
  Http.server.serve(":8080", api.toServiceAs[Text.Plain])
}
```

See [examples](examples/src/main/scala/io/finch) sub-project for more complete examples.

Performance
-----------

We use [wrk][wrk] to load test [Finch+Circe][finch-bench] against [Finagle+Jackson][finagle-bench]
to get some insight on how much overhead, an idiomatic Finch application written in a purely
functional way, involves on top of Finagle/Jackson. The results are quite impressive (for a pre-1.0
version): Finch performs on **95% of Finagle's throughput**.

Here is the first three runs of the benchmark on 2013 MB Pro (2.8 GHz Intel Core i7 w/ 16G RAM).

| Benchmark         | Run 1          | Run 2          | Run 3          |
|-------------------|----------------|----------------|----------------|
| Finagle + Jackson | 29014.68 req/s | 36783.21 req/s | 39924.42 req/s |
| Finch + Circe     | 28762.84 req/s | 36876.30 req/s | 37447.52 req/s |

Finch is also load tested against a number of Scala HTTP frameworks and libraries as part of the
[TechEmpower benchmark][tempower]. The most recent round showed that Finch performs really well
there, [scoring a second place][finch-is-fast] across all the Scala libraries.

Documentation
-------------
* The main documentation is hosted at http://finagle.github.io/finch/
* The documentation source may be found in the [`docs/`](docs/) folder
* The latest Scaladoc is available at http://finagle.github.io/finch/api/

Adopters
--------
* [Despegar](http://www.despegar.com)
* [Earnest](http://meetearnest.com)
* [Globo.com](http://globo.com)
* [Glopart](https://glopart.ru)
* [Hotel Urbano](http://www.hotelurbano.com)
* [Konfettin](http://konfettin.ru)
* [JusBrasil](http://www.jusbrasil.com.br)
* [Sabre Labs](http://sabrelabs.com)
* [Spright](http://spright.com)
* [SoFi](https://www.sofi.com/)
* [Qubit](http://www.qubit.com)
* [QuizUp](http://www.quizup.com)
* [Lookout](http://www.lookout.com)
* [Project September](http://projectseptember.com/)
* [Sigma](http://thesigma.com)
* [D.A.Consortium](http://www.dac.co.jp/english/)
* [Redbubble](https://redbubble.com/)
* [Zalando](https://zalando.de)
* [Rakuten](http://rakuten.co.jp)
* [Threat Stack](https://www.threatstack.com/)
* [RelinkLabs](https://relinklabs.com/)
* *Submit a pull-request to include your company/project into the list*

Related Projects
----------------

* [Finch Template](https://github.com/redbubble/finch-template): A Redbubble approach to building services with Finch
* [Finch Server](https://github.com/BenWhitehead/finch-server): Finch's integration into TwitterServer
* [Finch Sangria](https://github.com/redbubble/finch-sangria): Finch's GraphQL support
* [Finch Demo](https://github.com/slouc/finch-demo): Community-maintained Finch user guide
* [Finch Rich](https://github.com/akozhemiakin/finchrich): Macro-based controllers for Finch
* [Finch Quickstart](https://github.com/zdavep/finch-quickstart): A skeleton Finch project
* [Finch OAuth2](https://github.com/finch/finch-oauth2): the OAuth2 support backed by the [finagle-oauth2][finagle-oauth2] library

Contributing
------------
There are plenty of ways to contribute into Finch:

* Give it a star
* Join the [Gitter][gitter] room and leave a feedback or help with answering users' questions
* [Submit a PR](CONTRIBUTING.md) (there is an issue label ["easy"](https://github.com/finagle/finch/issues?q=is%3Aopen+is%3Aissue+label%3Aeasy) for newcomers)
* Be cool and wear a [Finch T-Shirt](http://www.redbubble.com/people/vkostyukov/works/13277123-finch-io-rest-api-with-finagle?p=t-shirt)

The Finch project supports the [Typelevel][typelevel] [code of conduct][conduct] and wants all of its channels
(Gitter, GitHub, etc.) to be welcoming environments for everyone.

Finch is currently maintained by [Vladimir Kostyukov][vkostyukov], [Travis Brown][travisbrown],
[Ryan Plessner][ryan_plessner], and [Sergey Kolbasov][sergey_kolbasov]. After the 1.0 release, all
pull requests will require two sign-offs by a maintainer to be merged.

License
-------
Licensed under the **[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[gitter]: https://gitter.im/finagle/finch
[finagle]: https://github.com/twitter/finagle
[circe]: https://github.com/circe/circe
[jackson]: http://wiki.fasterxml.com/JacksonHome
[argonaut]: http://argonaut.io
[finagle-oauth2]: https://github.com/finagle/finagle-oauth2
[json4s]: http://json4s.org
[playjson]: https://www.playframework.com/documentation/2.6.x/ScalaJson
[sprayjson]: https://github.com/spray/spray-json
[iteratee]: https://github.com/travisbrown/iteratee
[refined]: https://github.com/fthomas/refined
[scaladoc]: http://finagle.github.io/finch/docs/#io.finch.package
[typelevel]: http://typelevel.org/
[conduct]: http://typelevel.org/conduct.html
[wrk]: https://github.com/wg/wrk
[finch-bench]: https://github.com/finagle/finch/blob/master/examples/src/main/scala/io/finch/wrk/Finch.scala
[finagle-bench]: https://github.com/finagle/finch/blob/master/examples/src/main/scala/io/finch/wrk/Finagle.scala
[finagle-oauth2]: https://github.com/finagle/finagle-oauth2
[tempower]: https://www.techempower.com/benchmarks/#section=data-r12&hw=peak&test=json&l=6bk
[finch-is-fast]: http://vkostyukov.net/posts/how-fast-is-finch/
[finch-jackson]: https://github.com/finch/finch-jackson
[finch-json4s]: https://github.com/finch/finch-json4s
[finch-sprayjson]: https://github.com/finch/finch-sprayjson
[finch-playjson]: https://github.com/finch/finch-playjson
[finch-oauth2]: https://github.com/finch/finch-ouath2
[server-sent-events]: https://en.wikipedia.org/wiki/Server-sent_events
[vkostyukov]: https://twitter.com/vkostyukov
[travisbrown]: https://twitter.com/travisbrown
[ryan_plessner]: https://twitter.com/ryan_plessner
[sergey_kolbasov]: https://twitter.com/sergey_kolbasov
