package io.eleven19.ascribe.asg

import zio.blocks.chunk.Chunk

/** Base class for all ASG nodes. Every node has a name (type discriminator)
  * and a nodeType (category: "block", "inline", or "string").
  * These are immutable and determined by the concrete type.
  */
sealed abstract class Node(val name: String, val nodeType: String):
  def location: Location

/** Base class for block-level ASG nodes. All blocks have type "block"
  * and share optional id, title, reftext, and metadata fields.
  */
sealed abstract class Block(name: String) extends Node(name, "block"):
  def id: Option[String]
  def title: Option[Chunk[Inline]]
  def reftext: Option[Chunk[Inline]]
  def metadata: Option[BlockMetadata]

/** Base class for inline ASG nodes. Inline nodes have varying nodeType:
  * parent inlines (Span, Ref) use "inline", literal inlines (Text, CharRef, Raw) use "string".
  */
sealed abstract class Inline(name: String, nodeType: String) extends Node(name, nodeType)
