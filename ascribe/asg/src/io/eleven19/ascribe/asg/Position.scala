package io.eleven19.ascribe.asg

import zio.blocks.chunk.Chunk
import zio.blocks.schema.Schema

/** A source position identified by line and column (both 1-based). */
case class Position(
    line: Int,
    col: Int,
    file: Option[Chunk[String]] = None
) derives Schema

/** A source location as a pair of positions (start, end). */
type Location = Chunk[Position]
