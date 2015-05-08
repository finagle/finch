package io.finch.benchmarks.service

import org.json4s.{DefaultFormats, Formats}

package object json4s {
  implicit val formats: Formats = DefaultFormats
}
