package io.eleven19.ascribe.asg

import kyo.Chunk

/** A source position identified by line and column (both 1-based). */
case class Position(
    line: Int,
    col: Int,
    file: Option[Chunk[String]] = None
)

/** A source location as a pair of positions (start, end). Serializes as a JSON array to match the TCK schema. */
case class Location(start: Position, end: Position)
