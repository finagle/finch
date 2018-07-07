package io.finch

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

/**
 * Models a trace of a matched [[Endpoint]]. For example, `/hello/:name`.
 *
 * @note represented as a linked-list-like structure for efficiency.
 */
sealed trait Trace {

  /**
   * Concatenates this and `that` [[Trace]]s.
   */
  final def concat(that: Trace): Trace = {
    @tailrec
    def loop(from: Trace, last: Trace.Segment): Unit = from match {
      case Trace.Empty =>
        last.next = that
      case Trace.Segment(p, n) =>
        val newLast = Trace.Segment(p, Trace.Empty)
        last.next = newLast
        loop(n, newLast)
    }

    this match {
      case Trace.Empty => that
      case a @ Trace.Segment(_, _) => that match {
        case Trace.Empty => a
        case _ =>
          val result = Trace.Segment(a.path, Trace.Empty)
          loop(a.next, result)
          result
      }
    }
  }

  /**
   * Converts this [[Trace]] into a linked list of path segments.
   */
  final def toList: List[String] = {
    @tailrec
    def loop(from: Trace, to: ListBuffer[String]): List[String] = from match {
      case Trace.Empty => to.toList
      case Trace.Segment(path, next) => loop(next, to += path)
    }

    loop(this, ListBuffer.empty)
  }

  final override def toString: String = toList.mkString("/", "/", "")
}

object Trace {
  private case object Empty extends Trace
  private final case class Segment(path: String, var next: Trace) extends Trace

  def empty: Trace = Empty
  def segment(s: String): Trace = Segment(s, empty)
}
