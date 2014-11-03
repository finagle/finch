![logo](https://raw.github.com/finagle/finch/master/finch-logo.png) 

Hi! I'm **Finch.io**, a thin layer of purely functional basic blocks atop of  [Finagle](http://twitter.github.io/finagle) for building robust and composable REST APIs.

Quickstart
----------

```scala
resolvers += "Finch.io" at "http://repo.konfettin.ru"

libraryDependencies ++= Seq(
  "io" %% "finch" % "0.1.6"
)
```

```scala
def hello(name: String) = new Service[HttpRequest, HttpResponse] = {
  def apply(req: HttpRequest) = for {
    title <- OptionalParam("title")(req)
  } yield Ok(s"Hello, ${title.getOrElse("")} $name!")
}

val endpoint = new Endpoint[HttpRequest, HttpResponse] {
  def route = {
    // routes requests like '/hello/Bob?title=Mr.'
    case Method.Get -> Root / "hello" / name => hello(name)
  }
}
```

Documentation
-------------
Documentation may be found at [`docs`](docs/index.md) folder of the repository.

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
