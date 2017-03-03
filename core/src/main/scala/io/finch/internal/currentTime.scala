package io.finch.internal

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.Locale


object currentTime {
  private[this] val formatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")
      .withLocale(Locale.ENGLISH)
      .withZone(ZoneId.of("GMT"))

  @volatile private[this] var last: (Long, String) = (0, "")

  def apply(): String = {
    val time = System.currentTimeMillis()
    if (time - last._1 > 1000) {
      last = time -> formatter.format(Instant.ofEpochMilli(time))
    }

    last._2
  }
}
