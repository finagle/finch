package io.finch.internal

import com.twitter.io.Buf
import io.finch.FinchSpec
import java.nio.charset.Charset

class HttpContentSpec extends FinchSpec {

  behavior of "HttpContet"

  it should "asByteArrayWithBeginAndEnd" in {
    check { b: Buf =>
      val (array, begin, end) = b.asByteArrayWithBeginAndEnd
      Buf.ByteArray.Owned.extract(b) === array.slice(begin, end)
    }
  }

  it should "asByteBuffer" in {
    check { b: Buf =>
      b.asByteBuffer === Buf.ByteBuffer.Owned.extract(b)
    }
  }

  it should "asByteArray" in {
    check { b: Buf =>
      b.asByteArray === Buf.ByteArray.Owned.extract(b)
    }
  }

  it should "asString" in {
    check { (b: Buf, cs: Charset) =>
      b.asString(cs) === new String(Buf.ByteArray.Owned.extract(b), cs)
    }
  }
}
