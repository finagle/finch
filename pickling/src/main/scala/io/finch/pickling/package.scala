package io.finch

import com.twitter.util.Try
import io.finch.internal.BufText

package object pickling {

  import scala.pickling._
  import scala.pickling.Defaults._

  implicit def encodePickling[A](implicit pickler: Pickler[A],
                                 format: PickleFormat,
                                 f: Pickle => String): Encode.Json[A] =
    Encode.json((a, cs) => BufText(f(a.pickle(format, pickler)), cs))

  implicit def decodePickling[A](implicit unpickler: Unpickler[A],
                                 format: scala.pickling.PickleFormat,
                                 f: String => Pickle): Decode[A] =
    Decode.instance(input => Try(f(input).unpickle[A](unpickler, format)))

}
