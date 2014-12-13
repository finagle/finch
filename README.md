<p align="center">
  <img src="https://raw.githubusercontent.com/finagle/finch/master/finch-logo.png" width="360px" />
</p>

Finch is a thin layer of purely functional basic blocks atop of [Finagle](http://twitter.github.io/finagle) for 
building composable REST APIs. Its mission is to provide the developers simple and robust REST API building blocks 
being as close as possible to the Finagle bare metal API.

Modules
-------

Finch uses multi-project structure and contains of the following _modules_:

* `finch-core` - the core classes/functions
* `finch-json` - the lightweight and  immutable JSON API
* `finch-demo` - the demo project
* `finch-jawn` - the JSON API support for the [Jawn](https://github.com/non/jawn) library
* `finch-argonaut` - the JSON API support for the [Argonaut](http://argonaut.io/) library

Installation 
------------
Every Finch module is published at Maven Central. Use the following _sbt_ snippet:

* For _stable_ release:
 
```scala
libraryDependencies ++= Seq(
  "com.github.finagle" %% "finch-module" % "0.2.0"
)

```

* For `SNAPSHOT` version:

```scala
resolvers ++= Seq(
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

libraryDependencies ++= Seq(
  "com.github.finagle" %% "finch-module" % "0.3.0-SNAPSHOT" changing()
)
```

Quickstart
----------

```scala
libraryDependencies ++= Seq(
  "com.github.finagle" %% "finch-core" % "0.2.0",
  "com.github.finagle" %% "finch-json" % "0.2.0"
)
```

```scala
def hello(name: String) = new Service[HttpRequest, HttpResponse] = {
  def apply(req: HttpRequest) = for {
    title <- OptionalParam("title")(req)
  } yield Ok(Json.obj("greetings" -> s"Hello, ${title.getOrElse("")} $name!"))
}

val endpoint = Endpoint[HttpRequest, HttpResponse] {
    // routes requests like '/hello/Bob?title=Mr.'
    case Method.Get -> Root / "hello" / name => hello(name)
  }
}
```

Documentation
-------------
Documentation may be found in the [`docs.md`](docs.md) file in the root directory.

Adopters
--------
* [Konfettin](http://konfettin.ru)
* [JusBrasil](http://www.jusbrasil.com.br)
* *Submit a pull-request to include your company/project into the list*

License
-------

Licensed under the **[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

----
[![Build Status](https://secure.travis-ci.org/finagle/finch.png)](http://travis-ci.org/finagle/finch)
[![Coverage Status](https://coveralls.io/repos/finagle/finch/badge.png)](https://coveralls.io/r/finagle/finch)
