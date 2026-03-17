package io.eleven19.ascribe.asg

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{Modifier, Schema}

/** Base trait for all ASG nodes. Every node has a location and a nodeType (category: "block", "inline", or "string").
  */
sealed trait Node derives Schema:
    def location: Location
    def nodeType: String

/** Base trait for block-level ASG nodes. All blocks have type "block" and share optional id, title, reftext, and
  * metadata fields.
  */
sealed trait Block extends Node derives Schema:
    def id: Option[String]
    def title: Option[Chunk[Inline]]
    def reftext: Option[Chunk[Inline]]
    def metadata: Option[BlockMetadata]

/** Base trait for inline ASG nodes. Inline nodes have varying nodeType: parent inlines (Span, Ref) use "inline",
  * literal inlines (Text, CharRef, Raw) use "string".
  */
sealed trait Inline extends Node derives Schema

// === Block subtypes ===

// --- Document (extends Node directly, not Block — per ASG schema) ---

case class Document private (
    attributes: Option[Map[String, Option[String]]],
    header: Option[Header],
    blocks: Chunk[Block],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Node derives Schema

object Document:

    def apply(
        attributes: Option[Map[String, Option[String]]] = None,
        header: Option[Header] = None,
        blocks: Chunk[Block] = Chunk.empty,
        location: Location
    ): Document = new Document(attributes, header, blocks, location, "block")

// --- Section and discrete Heading ---

case class Section private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    level: Int,
    blocks: Chunk[Block],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Section:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        level: Int,
        blocks: Chunk[Block] = Chunk.empty,
        location: Location
    ): Section = new Section(id, title, reftext, metadata, level, blocks, location, "block")

case class Heading private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    level: Int,
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Heading:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        level: Int,
        location: Location
    ): Heading = new Heading(id, title, reftext, metadata, level, location, "block")

// --- Leaf blocks ---

case class Paragraph private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: Option[String],
    delimiter: Option[String],
    inlines: Chunk[Inline],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Paragraph:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: Option[String] = None,
        delimiter: Option[String] = None,
        inlines: Chunk[Inline] = Chunk.empty,
        location: Location
    ): Paragraph = new Paragraph(id, title, reftext, metadata, form, delimiter, inlines, location, "block")

case class Listing private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: Option[String],
    delimiter: Option[String],
    inlines: Chunk[Inline],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Listing:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: Option[String] = None,
        delimiter: Option[String] = None,
        inlines: Chunk[Inline] = Chunk.empty,
        location: Location
    ): Listing = new Listing(id, title, reftext, metadata, form, delimiter, inlines, location, "block")

case class Literal private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: Option[String],
    delimiter: Option[String],
    inlines: Chunk[Inline],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Literal:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: Option[String] = None,
        delimiter: Option[String] = None,
        inlines: Chunk[Inline] = Chunk.empty,
        location: Location
    ): Literal = new Literal(id, title, reftext, metadata, form, delimiter, inlines, location, "block")

case class Pass private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: Option[String],
    delimiter: Option[String],
    inlines: Chunk[Inline],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Pass:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: Option[String] = None,
        delimiter: Option[String] = None,
        inlines: Chunk[Inline] = Chunk.empty,
        location: Location
    ): Pass = new Pass(id, title, reftext, metadata, form, delimiter, inlines, location, "block")

case class Stem private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: Option[String],
    delimiter: Option[String],
    inlines: Chunk[Inline],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Stem:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: Option[String] = None,
        delimiter: Option[String] = None,
        inlines: Chunk[Inline] = Chunk.empty,
        location: Location
    ): Stem = new Stem(id, title, reftext, metadata, form, delimiter, inlines, location, "block")

case class Verse private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: Option[String],
    delimiter: Option[String],
    inlines: Chunk[Inline],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Verse:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: Option[String] = None,
        delimiter: Option[String] = None,
        inlines: Chunk[Inline] = Chunk.empty,
        location: Location
    ): Verse = new Verse(id, title, reftext, metadata, form, delimiter, inlines, location, "block")

// --- Parent blocks ---

case class Sidebar private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: String,
    delimiter: String,
    blocks: Chunk[Block],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Sidebar:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: String,
        delimiter: String,
        blocks: Chunk[Block] = Chunk.empty,
        location: Location
    ): Sidebar = new Sidebar(id, title, reftext, metadata, form, delimiter, blocks, location, "block")

case class Example private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: String,
    delimiter: String,
    blocks: Chunk[Block],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Example:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: String,
        delimiter: String,
        blocks: Chunk[Block] = Chunk.empty,
        location: Location
    ): Example = new Example(id, title, reftext, metadata, form, delimiter, blocks, location, "block")

case class Admonition private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: String,
    delimiter: String,
    variant: String,
    blocks: Chunk[Block],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Admonition:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: String,
        delimiter: String,
        variant: String,
        blocks: Chunk[Block] = Chunk.empty,
        location: Location
    ): Admonition = new Admonition(id, title, reftext, metadata, form, delimiter, variant, blocks, location, "block")

case class Open private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: String,
    delimiter: String,
    blocks: Chunk[Block],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Open:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: String,
        delimiter: String,
        blocks: Chunk[Block] = Chunk.empty,
        location: Location
    ): Open = new Open(id, title, reftext, metadata, form, delimiter, blocks, location, "block")

case class Quote private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: String,
    delimiter: String,
    blocks: Chunk[Block],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Quote:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: String,
        delimiter: String,
        blocks: Chunk[Block] = Chunk.empty,
        location: Location
    ): Quote = new Quote(id, title, reftext, metadata, form, delimiter, blocks, location, "block")

// --- Lists ---

case class List private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    variant: String,
    marker: String,
    items: Chunk[ListItem],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object List:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        variant: String,
        marker: String,
        items: Chunk[ListItem],
        location: Location
    ): List = new List(id, title, reftext, metadata, variant, marker, items, location, "block")

@Modifier.rename("dlist")
case class DList private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    marker: String,
    items: Chunk[DListItem],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object DList:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        marker: String,
        items: Chunk[DListItem],
        location: Location
    ): DList = new DList(id, title, reftext, metadata, marker, items, location, "block")

case class ListItem private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    marker: String,
    principal: Chunk[Inline],
    blocks: Chunk[Block],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object ListItem:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        marker: String,
        principal: Chunk[Inline],
        blocks: Chunk[Block] = Chunk.empty,
        location: Location
    ): ListItem = new ListItem(id, title, reftext, metadata, marker, principal, blocks, location, "block")

@Modifier.rename("dlistItem")
case class DListItem private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    marker: String,
    terms: Chunk[Chunk[Inline]],
    principal: Option[Chunk[Inline]],
    blocks: Chunk[Block],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object DListItem:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        marker: String,
        terms: Chunk[Chunk[Inline]],
        principal: Option[Chunk[Inline]] = None,
        blocks: Chunk[Block] = Chunk.empty,
        location: Location
    ): DListItem = new DListItem(id, title, reftext, metadata, marker, terms, principal, blocks, location, "block")

// --- Break ---

case class Break private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    variant: String,
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Break:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        variant: String,
        location: Location
    ): Break = new Break(id, title, reftext, metadata, variant, location, "block")

// --- Block macros (each a concrete type with fixed name) ---

case class Audio private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: String,
    target: Option[String],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Audio:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: String = "macro",
        target: Option[String] = None,
        location: Location
    ): Audio = new Audio(id, title, reftext, metadata, form, target, location, "block")

case class Video private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: String,
    target: Option[String],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Video:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: String = "macro",
        target: Option[String] = None,
        location: Location
    ): Video = new Video(id, title, reftext, metadata, form, target, location, "block")

case class Image private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: String,
    target: Option[String],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Image:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: String = "macro",
        target: Option[String] = None,
        location: Location
    ): Image = new Image(id, title, reftext, metadata, form, target, location, "block")

case class Toc private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: String,
    target: Option[String],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Toc:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: String = "macro",
        target: Option[String] = None,
        location: Location
    ): Toc = new Toc(id, title, reftext, metadata, form, target, location, "block")

// === Inline subtypes ===

// --- Parent inlines (contain child inlines, nodeType = "inline") ---

/** Inline formatting span (strong, emphasis, code, mark). */
case class Span private (
    variant: String,
    form: String,
    inlines: Chunk[Inline],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Inline derives Schema

object Span:

    def apply(variant: String, form: String, inlines: Chunk[Inline], location: Location): Span =
        new Span(variant, form, inlines, location, "inline")

/** Inline reference (link, xref). */
case class Ref private (
    variant: String,
    target: String,
    inlines: Chunk[Inline],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Inline derives Schema

object Ref:

    def apply(variant: String, target: String, inlines: Chunk[Inline], location: Location): Ref =
        new Ref(variant, target, inlines, location, "inline")

// --- Literal inlines (leaf nodes with string values, nodeType = "string") ---

/** Plain text content. */
case class Text private (
    value: String,
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Inline derives Schema

object Text:

    def apply(value: String, location: Location): Text =
        new Text(value, location, "string")

/** Character reference. */
@Modifier.rename("charref")
case class CharRef private (
    value: String,
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Inline derives Schema

object CharRef:

    def apply(value: String, location: Location): CharRef =
        new CharRef(value, location, "string")

/** Raw (passthrough) inline content. */
case class Raw private (
    value: String,
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Inline derives Schema

object Raw:

    def apply(value: String, location: Location): Raw =
        new Raw(value, location, "string")
