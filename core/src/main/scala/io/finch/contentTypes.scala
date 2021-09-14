package io.finch

/**
 * @see
 *   [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types]]
 */
object Application {
  type Json = "application/json"
  type Xml = "application/xml"
  type AtomXml = "application/atom+xml"
  type Csv = "application/csv"
  type Javascript = "application/javascript"
  type OctetStream = "application/octet-stream"
  type RssXml = "application/rss+xml"
  type WwwFormUrlencoded = "application/x-www-form-urlencoded"
  type Ogg = "application/ogg"
}

/**
 * @see
 *   [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types]]
 */
object Text {
  type Plain = "text/plain"
  type Html = "text/html"
  type Css = "text/css"
  type EventStream = "text/event-stream"
}

/**
 * @see
 *   [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types]]
 */
object Image {
  type Gif = "image/gif"
  type Jpeg = "image/jpeg"
  type Png = "image/png"
  type Svg = "image/svg+xml"
}

/**
 * @see
 *   [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types]]
 */
object Audio {
  type Wave = "audio/wave"
  type Wav = "audio/wav"
  type Webm = "audio/webm"
  type Ogg = "audio/ogg"
}
