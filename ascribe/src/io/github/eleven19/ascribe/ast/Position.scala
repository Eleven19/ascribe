package io.github.eleven19.ascribe.ast

/** A source position identified by line and column (both 1-based, as returned by Parsley's `pos`). */
case class Position(line: Int, col: Int)

/** A source span from start to end position. */
case class Span(start: Position, end: Position)

object Span:
    /** Sentinel for tests and contexts where position is irrelevant. Scala case class equals only considers the first
      * parameter list, so nodes created with Span.unknown still compare equal to nodes with real spans.
      */
    val unknown: Span = Span(Position(0, 0), Position(0, 0))
