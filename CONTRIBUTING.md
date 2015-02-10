Generally, Finch follows a standard [fork and pull][0] model for contributions via GitHub pull requests. Thus, the
_contributing process_ looks as follows:

1. [Write code](#write-code)
2. [Write tests](#write-tests)
3. [Write docs](#write-docs)
4. [Submit a PR](#submit-a-pr)

## Write Code
Finch follows the [Effective Scala][1] code style guide. When in doubt, look around the codebase and see how it's done
elsewhere.

* Code and comments should be formatted to a width no greater than 120 columns
* Files should be exempt of trailing spaces
* Each abstraction should live in its own Scala file, i.e `RequestReader.scala`
* Each implementation should live in the package object, i.e, `io.finch.request`

That said, the Scala style checker `sbt scalastyle` should pass on the code. 

## Write Tests
Finch uses [ScalaTest][2] with the following settings:

* Every test should be a `FlatSpec` with `Matchers` mixed in
* An assertion should be done with `x shouldBe y`
* Exceptions should be intercepted with `an [Exception] shouldBe thrownBy(x)`

## Write Docs
Write clean and simple docs in the `docs.md` file.

## Submit a PR
* PR should be submitted from a separate branch (use `git checkout -b "fix-123"`)
* PR should contain only one commit (use `git commit --amend` and `git --force push`)
* PR should not decrease the code coverage more than by 1%
* PR's commit message should use present tense and be capitalized properly (i.e., `Fix #123: Add tests for RequestReader`)

[0]: https://help.github.com/articles/using-pull-requests/
[1]: http://twitter.github.io/effectivescala/
[2]: http://www.scalatest.org/
