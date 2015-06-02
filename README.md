<p align="center">
  <img src="https://raw.githubusercontent.com/finagle/finch/master/finch-logo.png" width="500px" />
</p>

Finch is a thin layer of purely functional basic blocks atop of [Finagle](http://twitter.github.io/finagle) for
building composable REST APIs. Its mission is to provide the developers simple and robust REST API primitives being as
close as possible to the bare metal Finagle API.

Badges
------
[![Build Status](https://img.shields.io/travis/finagle/finch/master.svg)](https://travis-ci.org/finagle/finch)
[![Coverage Status](https://img.shields.io/coveralls/finagle/finch/master.svg)](https://coveralls.io/r/finagle/finch?branch=master)
[![Gitter](https://img.shields.io/badge/gitter-join%20chat-green.svg)](https://gitter.im/finagle/finch?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.finagle/finch_2.11.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.finagle/finch_2.11)

Modules
-------

Finch uses multi-project structure and contains of the following _modules_:

* [`finch-core`](core) - the core classes/functions
* [`finch-json`](json) - the lightweight and immutable JSON API. __DEPRECATED__
* [`finch-auth`](auth) - the Basic HTTP Auth support
* [`finch-demo`](demo) - the demo project
* [`finch-playground`](playground) - the playground project
* [`finch-jawn`](jawn) - the JSON API support for the [Jawn](https://github.com/non/jawn) library
* [`finch-argonaut`](argonaut) - the JSON API support for the [Argonaut](http://argonaut.io/) library
* [`finch-jackson`](jackson) - the JSON API support for the [Jackson](http://jackson.codehaus.org/) library
* [`finch-json4s`](json4s) - the JSON API support for the [JSON4S](http://json4s.org/) library

Installation
------------
Every Finch module is published at Maven Central. Use the following _sbt_ snippet ...

* for the _stable_ release:

```scala
libraryDependencies ++= Seq(
  "com.github.finagle" %% "[finch-module]" % "0.6.0"
)
```

* for the `SNAPSHOT` version:

```scala
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "com.github.finagle" %% "[finch-module]" % "0.7.0-SNAPSHOT" changing()
)
```

Hello World!
------------
This "Hello World!" example is built with the `0.7.0-SNAPSHOT` version of `finch-core`.

```scala
import io.finch.route._
import com.twitter.finagle.Httpx

Httpx.serve(":8080", (Get / "hello" /> "Hello, World!").toService)
```

Documentation
-------------
* A comprehensive documentation may be found in the [`docs/`](docs/index.md) folder
* The latest Scaladoc is available at [http://finagle.github.io/finch/docs](http://finagle.github.io/finch/docs/#io.finch.package)

Adopters
--------
* [Konfettin](http://konfettin.ru)
* [JusBrasil](http://www.jusbrasil.com.br)
* [Sabre Labs](http://sabrelabs.com)
* [Spright](http://spright.com)
* [SoFi] (https://www.sofi.com/)
* [Qubit] (http://www.qubit.com)
* *Submit a pull-request to include your company/project into the list*

Contributing
------------

There are plenty of ways to contribute into Finch:

* Give it a star
* Join the [Gitter][1] room and leave a feedback or help with answering users' questions
* [Submit a PR](CONTRIBUTING.md) (there is an issue label ["easy"](https://github.com/finagle/finch/issues?q=is%3Aopen+is%3Aissue+label%3Aeasy) for newcomers)
* Be cool and wear a [Finch T-Shirt](http://www.redbubble.com/people/vkostyukov/works/13277123-finch-io-rest-api-with-finagle?p=t-shirt)

License
-------

Licensed under the **[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[1]: https://gitter.im/finagle/finch
