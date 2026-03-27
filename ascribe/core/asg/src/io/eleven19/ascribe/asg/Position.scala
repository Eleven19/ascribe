package io.eleven19.ascribe.asg

import zio.blocks.chunk.Chunk
import zio.blocks.schema.Schema

/** A source position identified by line and column (both 1-based). */
case class Position(
    line: Int,
    col: Int,
    file: Option[Chunk[String]] = None
) derives Schema

/** A source location as a pair of positions (start, end). Serializes as a JSON array to match the TCK schema. */
case class Location(start: Position, end: Position)

object Location:

    given Schema[Location] = summon[Schema[Chunk[Position]]].transform[Location](
        chunk => Location(chunk(0), chunk(1)),
        loc => Chunk(loc.start, loc.end)
    )
