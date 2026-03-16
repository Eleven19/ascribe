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

// === Block subtypes ===

// --- Document (extends Node directly, not Block — per ASG schema) ---

case class Document(
    attributes: Option[Map[String, Option[String]]] = None,
    header: Option[Header] = None,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Node("document", "block")

// --- Section and discrete Heading ---

case class Section(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    level: Int,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("section")

case class Heading(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    level: Int,
    location: Location
) extends Block("heading")

// --- Leaf blocks ---

case class Paragraph(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: Option[String] = None,
    delimiter: Option[String] = None,
    inlines: Chunk[Inline] = Chunk.empty,
    location: Location
) extends Block("paragraph")

case class Listing(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: Option[String] = None,
    delimiter: Option[String] = None,
    inlines: Chunk[Inline] = Chunk.empty,
    location: Location
) extends Block("listing")

case class Literal(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: Option[String] = None,
    delimiter: Option[String] = None,
    inlines: Chunk[Inline] = Chunk.empty,
    location: Location
) extends Block("literal")

case class Pass(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: Option[String] = None,
    delimiter: Option[String] = None,
    inlines: Chunk[Inline] = Chunk.empty,
    location: Location
) extends Block("pass")

case class Stem(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: Option[String] = None,
    delimiter: Option[String] = None,
    inlines: Chunk[Inline] = Chunk.empty,
    location: Location
) extends Block("stem")

case class Verse(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: Option[String] = None,
    delimiter: Option[String] = None,
    inlines: Chunk[Inline] = Chunk.empty,
    location: Location
) extends Block("verse")

// --- Parent blocks ---

case class Sidebar(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String,
    delimiter: String,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("sidebar")

case class Example(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String,
    delimiter: String,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("example")

case class Admonition(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String,
    delimiter: String,
    variant: String,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("admonition")

case class Open(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String,
    delimiter: String,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("open")

case class Quote(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String,
    delimiter: String,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("quote")

// --- Lists ---

case class List(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    variant: String,
    marker: String,
    items: Chunk[ListItem],
    location: Location
) extends Block("list")

case class DList(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    marker: String,
    items: Chunk[DListItem],
    location: Location
) extends Block("dlist")

case class ListItem(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    marker: String,
    principal: Chunk[Inline],
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("listItem")

case class DListItem(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    marker: String,
    terms: Chunk[Chunk[Inline]],
    principal: Option[Chunk[Inline]] = None,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("dlistItem")

// --- Break ---

case class Break(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    variant: String,
    location: Location
) extends Block("break")

// --- Block macros (each a concrete type with fixed name) ---

case class Audio(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String = "macro",
    target: Option[String] = None,
    location: Location
) extends Block("audio")

case class Video(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String = "macro",
    target: Option[String] = None,
    location: Location
) extends Block("video")

case class Image(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String = "macro",
    target: Option[String] = None,
    location: Location
) extends Block("image")

case class Toc(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String = "macro",
    target: Option[String] = None,
    location: Location
) extends Block("toc")

// === Inline subtypes ===

// --- Parent inlines (contain child inlines, nodeType = "inline") ---

/** Inline formatting span (strong, emphasis, code, mark). */
case class Span(
    variant: String,
    form: String,
    inlines: Chunk[Inline],
    location: Location
) extends Inline("span", "inline")

/** Inline reference (link, xref). */
case class Ref(
    variant: String,
    target: String,
    inlines: Chunk[Inline],
    location: Location
) extends Inline("ref", "inline")

// --- Literal inlines (leaf nodes with string values, nodeType = "string") ---

/** Plain text content. */
case class Text(
    value: String,
    location: Location
) extends Inline("text", "string")

/** Character reference. */
case class CharRef(
    value: String,
    location: Location
) extends Inline("charref", "string")

/** Raw (passthrough) inline content. */
case class Raw(
    value: String,
    location: Location
) extends Inline("raw", "string")
