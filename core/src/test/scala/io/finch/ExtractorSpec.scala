package io.finch

import java.util.UUID

import com.twitter.util.Try

class ExtractorSpec extends FinchSpec {

  "An extracting Endpoint (string)" should "extract one path segment" in {
    check { (i: Input) =>
      awaitValue(string(i)) === i.headOption
    }
  }

  "An extracting Endpoint (int)" should "extract one path segment" in {
    check { i: Input =>
      awaitValue(int(i)) === i.headOption.flatMap(s => Try(s.toInt).toOption)
    }
  }

  "An extracting Endpoint (boolean)" should "extract one path segment" in {
    check { i: Input =>
      awaitValue(boolean(i)) === i.headOption.flatMap(s => Try(s.toBoolean).toOption)
    }
  }

  "An extracting Endpoint (long)" should "extract one path segment" in {
    check { i: Input =>
      awaitValue(long(i)) === i.headOption.flatMap(s => Try(s.toLong).toOption)
    }
  }

  "An extracting Endpoint (uuid)" should "extract one path segment" in {
    check { i: Input =>
      awaitValue(uuid(i)) === i.headOption.flatMap(s => Try(UUID.fromString(s)).toOption)
    }
  }
}
