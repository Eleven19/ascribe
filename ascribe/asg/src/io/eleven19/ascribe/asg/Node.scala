package io.eleven19.ascribe.asg

import kyo.Chunk

/** Base trait for all ASG nodes. Every node has a location and a nodeType (category: "block", "inline", or "string").
  */
sealed trait Node:
    def location: Location
    def nodeType: String

/** Base trait for block-level ASG nodes. All blocks have type "block" and share optional id, title, reftext, and
  * metadata fields.
  */
sealed trait Block extends Node:
    def id: Option[String]
    def title: Option[Chunk[Inline]]
    def reftext: Option[Chunk[Inline]]
    def metadata: Option[BlockMetadata]

/** Base trait for inline ASG nodes. Inline nodes have varying nodeType: parent inlines (Span, Ref) use "inline",
  * literal inlines (Text, CharRef, Raw) use "string".
  */
sealed trait Inline extends Node

// === Block subtypes ===

// --- Document (extends Node directly, not Block — per ASG schema) ---

case class Document private (
    attributes: Option[Map[String, Option[String]]],
    header: Option[Header],
    blocks: Chunk[Block],
    location: Location,
    nodeType: String
) extends Node

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    items: Chunk[Block],
    location: Location,
    nodeType: String
) extends Block

object List:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        variant: String,
        marker: String,
        items: Chunk[Block],
        location: Location
    ): List = new List(id, title, reftext, metadata, variant, marker, items, location, "block")

case class DList private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    marker: String,
    items: Chunk[Block],
    location: Location,
    nodeType: String
) extends Block

object DList:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        marker: String,
        items: Chunk[Block],
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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

object Break:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        variant: String,
        location: Location
    ): Break = new Break(id, title, reftext, metadata, variant, location, "block")

// --- Tables (spec-derived, no official TCK test cases yet) ---

@specStatusInfo(
    SpecStatus.SpecDerived,
    "Table ASG structure inferred from AsciiDoc Language spec; no TCK test cases exist yet"
)
case class Table private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: String,
    delimiter: String,
    columns: Option[Chunk[ColumnSpec]],
    header: Option[Chunk[Block]],
    rows: Chunk[Block],
    footer: Option[Chunk[Block]],
    frame: Option[String],
    grid: Option[String],
    stripes: Option[String],
    location: Location,
    nodeType: String
) extends Block

object Table:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: String = "delimited",
        delimiter: String = "|===",
        columns: Option[Chunk[ColumnSpec]] = None,
        header: Option[Chunk[Block]] = None,
        rows: Chunk[Block],
        footer: Option[Chunk[Block]] = None,
        frame: Option[String] = None,
        grid: Option[String] = None,
        stripes: Option[String] = None,
        location: Location
    ): Table = new Table(
        id,
        title,
        reftext,
        metadata,
        form,
        delimiter,
        columns,
        header,
        rows,
        footer,
        frame,
        grid,
        stripes,
        location,
        "block"
    )

@specStatusInfo(SpecStatus.SpecDerived)
case class TableRow private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    cells: Chunk[Block],
    location: Location,
    nodeType: String
) extends Block

object TableRow:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        cells: Chunk[Block],
        location: Location
    ): TableRow = new TableRow(id, title, reftext, metadata, cells, location, "block")

@specStatusInfo(SpecStatus.SpecDerived)
case class TableCell private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    style: Option[CellStyle],
    colSpan: Option[ColSpan],
    rowSpan: Option[RowSpan],
    dupCount: Option[DupCount],
    inlines: Chunk[Inline],
    blocks: Chunk[Block],
    location: Location,
    nodeType: String
) extends Block

object TableCell:

    /** Create a cell with inline content (default/d style). */
    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        style: Option[CellStyle] = None,
        colSpan: Option[ColSpan] = None,
        rowSpan: Option[RowSpan] = None,
        dupCount: Option[DupCount] = None,
        inlines: Chunk[Inline] = Chunk.empty,
        location: Location
    ): TableCell =
        new TableCell(
            id,
            title,
            reftext,
            metadata,
            style,
            colSpan,
            rowSpan,
            dupCount,
            inlines,
            Chunk.empty,
            location,
            "block"
        )

    /** Create a cell with block content (a style — nested documents, tables). */
    def withBlocks(
        style: Option[CellStyle] = None,
        colSpan: Option[ColSpan] = None,
        rowSpan: Option[RowSpan] = None,
        dupCount: Option[DupCount] = None,
        blocks: Chunk[Block],
        location: Location
    ): TableCell =
        new TableCell(
            None,
            None,
            None,
            None,
            style,
            colSpan,
            rowSpan,
            dupCount,
            Chunk.empty,
            blocks,
            location,
            "block"
        )

    private[asg] def fromWire(
        id: Option[String],
        title: Option[Chunk[Inline]],
        reftext: Option[Chunk[Inline]],
        metadata: Option[BlockMetadata],
        style: Option[CellStyle],
        colSpan: Option[ColSpan],
        rowSpan: Option[RowSpan],
        dupCount: Option[DupCount],
        inlines: Chunk[Inline],
        blocks: Chunk[Block],
        location: Location
    ): TableCell =
        new TableCell(
            id,
            title,
            reftext,
            metadata,
            style,
            colSpan,
            rowSpan,
            dupCount,
            inlines,
            blocks,
            location,
            "block"
        )

// --- Block macros (each a concrete type with fixed name) ---

case class Audio private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: String,
    target: Option[String],
    location: Location,
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
    nodeType: String
) extends Block

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
case class Span private[asg] (
    variant: String,
    form: String,
    inlines: Chunk[Inline],
    location: Location,
    nodeType: String
) extends Inline

object Span:

    def apply(variant: String, form: String, inlines: Chunk[Inline], location: Location): Span =
        new Span(variant, form, inlines, location, "inline")

/** Inline reference (link, xref). */
case class Ref private[asg] (
    variant: String,
    target: String,
    inlines: Chunk[Inline],
    location: Location,
    nodeType: String
) extends Inline

object Ref:

    def apply(variant: String, target: String, inlines: Chunk[Inline], location: Location): Ref =
        new Ref(variant, target, inlines, location, "inline")

// --- Literal inlines (leaf nodes with string values, nodeType = "string") ---

/** Plain text content. */
case class Text private[asg] (
    value: String,
    location: Location,
    nodeType: String
) extends Inline

object Text:

    def apply(value: String, location: Location): Text =
        new Text(value, location, "string")

/** Character reference. */
case class CharRef private[asg] (
    value: String,
    location: Location,
    nodeType: String
) extends Inline

object CharRef:

    def apply(value: String, location: Location): CharRef =
        new CharRef(value, location, "string")

/** Raw (passthrough) inline content. */
case class Raw private[asg] (
    value: String,
    location: Location,
    nodeType: String
) extends Inline

object Raw:

    def apply(value: String, location: Location): Raw =
        new Raw(value, location, "string")
