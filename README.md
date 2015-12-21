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

Modules
-------
Finch uses multi-project structure and contains of the following _modules_:

* [`finch-core`](core) - the core classes/functions
* [`finch-argonaut`](argonaut) - the JSON API support for the [Argonaut][argonaut] library
* [`finch-jackson`](jackson) - the JSON API support for the [Jackson][jackson] library
* [`finch-json4s`](json4s) - the JSON API support for the [JSON4S][json4s] library
* [`finch-circe`](circe) - the JSON API support for the [Circe][circe] library
* [`finch-test`](test) - the test support classes/functions
* [`finch-oauth2`](oauth2) - the OAuth2 support backed by the [finagle-oauth2][finagle-oauth2] library

Installation
------------
Every Finch module is published at Maven Central. Use the following _sbt_ snippet ...

* for the _stable_ release:

```scala
libraryDependencies ++= Seq(
  "com.github.finagle" %% "[finch-module]" % "0.9.2"
)
```

* for the `SNAPSHOT` version:

```scala
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "com.github.finagle" %% "[finch-module]" % "0.9.3-SNAPSHOT" changing()
)
```

Hello World!
------------
This "Hello World!" example is built with the `0.9.3-SNAPSHOT` version of `finch-core`.

```scala
import io.finch._
import com.twitter.finagle.Http

val api: Endpoint[String] = get("hello") { Ok("Hello, World!") }

Http.serve(":8080", api.toService)
```

See [examples](examples/src/main/scala/io/finch) sub-project for more complete examples.

Performance
-----------

We use end-to-end [service benchmark][service-benchmark] to compare performance between bare-bones
[Finagle][finagle] service using [Jackson][jackson] for JSON parsing/serialization against Finch service
empowered by [Circe][circe]. Roughly, the Finch + Circe configuration gives the overhead less than
**5%** in terms of performance and less than **10%** in terms of allocations, compared to Finagle +
Jackson.

**GET /JSON-Object**

```
Benchmark                                             Mode  Cnt          Score        Error   Unit

FinchCirce.getJsonObj                                 avgt   10        352.658 ±     49.267  ms/op
FinchCirce.getJsonObj:·gc.alloc.rate.norm             avgt   10  366909071.657 ±  19659.934   B/op
FinagleJackson.getJsonObj                             avgt   10        372.074 ±     57.819  ms/op
FinagleJackson.getJsonObj:·gc.alloc.rate.norm         avgt   10  328441359.200 ±  78088.424   B/op
```

**POST /JSON-Object**
```
Benchmark                                             Mode  Cnt      Score            Error   Unit

FinchCirce.postJsonObj                                avgt   10        243.935 ±     29.455  ms/op
FinchCirce.postJsonObj:·gc.alloc.rate.norm            avgt   10  399187222.400 ±  84523.131   B/op
FinagleJackson.postJsonObj                            avgt   10        235.564 ±     46.433  ms/op
FinagleJackson.postJsonObj:·gc.alloc.rate.norm        avgt   10  376365652.800 ± 169854.875   B/op
```

Documentation
-------------
* A comprehensive documentation may be found in the [`docs/`](docs/index.md) folder
* The latest Scaladoc is available at [http://finagle.github.io/finch/docs][scaladoc]

Adopters
--------
* [Despegar] (http://www.despegar.com)
* [Globo.com] (http://globo.com)
* [Glopart] (https://glopart.ru)
* [Hotel Urbano] (http://www.hotelurbano.com)
* [Konfettin](http://konfettin.ru)
* [JusBrasil](http://www.jusbrasil.com.br)
* [Sabre Labs](http://sabrelabs.com)
* [Spright](http://spright.com)
* [SoFi] (https://www.sofi.com/)
* [Qubit] (http://www.qubit.com)
* [QuizUp] (http://www.quizup.com)
* *Submit a pull-request to include your company/project into the list*

Contributing
------------
There are plenty of ways to contribute into Finch:

* Give it a star
* Join the [Gitter][gitter] room and leave a feedback or help with answering users' questions
* [Submit a PR](CONTRIBUTING.md) (there is an issue label ["easy"](https://github.com/finagle/finch/issues?q=is%3Aopen+is%3Aissue+label%3Aeasy) for newcomers)
* Be cool and wear a [Finch T-Shirt](http://www.redbubble.com/people/vkostyukov/works/13277123-finch-io-rest-api-with-finagle?p=t-shirt)

The Finch project supports the [Typelevel][typelevel] [code of conduct][conduct] and wants all of its channels
(Gitter, GitHub, etc.) to be welcoming environments for everyone.

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
[service-benchmark]: https://github.com/finagle/finch/blob/master/benchmarks/src/main/scala/io/finch/benchmarks/service/UserServiceBenchmark.scala
[finagle]: https://github.com/twitter/finagle
[circe]: https://github.com/travisbrown/circe
[jackson]: http://wiki.fasterxml.com/JacksonHome
[argonaut]: http://argonaut.io
[finagle-oauth2]: https://github.com/finagle/finagle-oauth2
[json4s]: http://json4s.org
[scaladoc]: http://finagle.github.io/finch/docs/#io.finch.package
[typelevel]: http://typelevel.org/
[conduct]: http://typelevel.org/conduct.html
