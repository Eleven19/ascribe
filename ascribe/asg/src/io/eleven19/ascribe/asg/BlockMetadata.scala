package io.eleven19.ascribe.asg

import zio.blocks.chunk.Chunk
import zio.blocks.schema.Schema

/** Metadata that can be attached to any block node. */
case class BlockMetadata(
    attributes: Map[String, String] = Map.empty,
    options: Chunk[String] = Chunk.empty,
    roles: Chunk[String] = Chunk.empty,
    location: Option[Location] = None
) derives Schema
