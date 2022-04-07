package io.finch

import shapeless.Witness

/**
  * @see [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types]]
  */
object Application {
  type Json = Witness.`"application/json"`.T
  type Xml = Witness.`"application/xml"`.T
  type AtomXml = Witness.`"application/atom+xml"`.T
  type Csv = Witness.`"application/csv"`.T
  type Javascript = Witness.`"application/javascript"`.T
  type OctetStream = Witness.`"application/octet-stream"`.T
  type RssXml = Witness.`"application/rss+xml"`.T
  type WwwFormUrlencoded = Witness.`"application/x-www-form-urlencoded"`.T
  type Ogg = Witness.`"application/ogg"`.T
}

/**
  * @see [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types]]
  */
object Text {
  type Plain = Witness.`"text/plain"`.T
  type Html = Witness.`"text/html"`.T
  type Css = Witness.`"text/css"`.T
  type EventStream = Witness.`"text/event-stream"`.T
}

/**
  * @see [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types]]
  */
object Image {
  type Gif = Witness.`"image/gif"`.T
  type Jpeg = Witness.`"image/jpeg"`.T
  type Png = Witness.`"image/png"`.T
  type Svg = Witness.`"image/svg+xml"`.T
}

/**
  * @see [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types]]
  */
object Audio {
  type Wave = Witness.`"audio/wave"`.T
  type Wav = Witness.`"audio/wav"`.T
  type Webm = Witness.`"audio/webm"`.T
  type Ogg = Witness.`"audio/ogg"`.T
}
