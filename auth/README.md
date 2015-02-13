The `finch-auth` module provides a `BasicallyAuthorize` filter that does [Basic HTTP Auth][1].

Installation
------------
Use the following _sbt_ snippet:

```scala
libraryDependencies ++= Seq(
  "com.github.finagle" %% "finch-auth" % "0.5.0"
)
```

Documentation
-------------
See [Basic HTTP Auth](/docs/auth.md#basic-http-auth) section.

[1]: http://en.wikipedia.org/wiki/Basic_access_authentication
