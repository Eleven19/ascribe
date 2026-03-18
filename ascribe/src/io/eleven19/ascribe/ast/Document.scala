package io.eleven19.ascribe.ast

/** Base trait for all AST nodes. Every node carries a source span. */
sealed trait AstNode:
    def span: Span

object AstNode:
    given CanEqual[AstNode, AstNode] = CanEqual.derived

/** A list of inline elements forming the content of a single line. */
type InlineContent = List[Inline]

/** An inline element within a paragraph, heading, or list item. */
sealed trait Inline extends AstNode

object Inline:
    given CanEqual[Inline, Inline] = CanEqual.derived

/** Plain text content. */
case class Text(content: String)(val span: Span) extends Inline derives CanEqual

/** Bold span, surrounded by double asterisks: **bold**. */
case class Bold(content: List[Inline])(val span: Span) extends Inline derives CanEqual

/** Italic span, surrounded by double underscores: __italic__. */
case class Italic(content: List[Inline])(val span: Span) extends Inline derives CanEqual

/** Monospace span, surrounded by double backticks: ``mono``. */
case class Mono(content: List[Inline])(val span: Span) extends Inline derives CanEqual

/** Constrained bold span, surrounded by single asterisks: *bold*. */
case class ConstrainedBold(content: List[Inline])(val span: Span) extends Inline derives CanEqual

object Text extends PosParserBridge1[String, Text]:
    def apply(content: String)(span: Span): Text = new Text(content)(span)

object Bold extends PosParserBridge1[List[Inline], Bold]:
    def apply(content: List[Inline])(span: Span): Bold = new Bold(content)(span)

object Italic extends PosParserBridge1[List[Inline], Italic]:
    def apply(content: List[Inline])(span: Span): Italic = new Italic(content)(span)

object Mono extends PosParserBridge1[List[Inline], Mono]:
    def apply(content: List[Inline])(span: Span): Mono = new Mono(content)(span)

object ConstrainedBold extends PosParserBridge1[List[Inline], ConstrainedBold]:
    def apply(content: List[Inline])(span: Span): ConstrainedBold = new ConstrainedBold(content)(span)

/** A single item in a list block. */
case class ListItem(content: InlineContent)(val span: Span) extends AstNode derives CanEqual

object ListItem extends PosParserBridge1[InlineContent, ListItem]:
    def apply(content: InlineContent)(span: Span): ListItem = new ListItem(content)(span)

/** A block-level element in an AsciiDoc document. */
sealed trait Block extends AstNode

object Block:
    given CanEqual[Block, Block] = CanEqual.derived

/** A section heading.
  *
  * @param level
  *   heading level 1-5 corresponding to = through =====
  * @param title
  *   the inline content forming the heading text
  */
case class Heading(level: Int, title: InlineContent)(val span: Span) extends Block derives CanEqual

/** A section: a heading with nested blocks collected until the next same-or-higher-level heading. */
case class Section(level: Int, title: InlineContent, blocks: List[Block])(val span: Span) extends Block derives CanEqual

/** A paragraph of one or more lines of inline content. */
case class Paragraph(content: InlineContent)(val span: Span) extends Block derives CanEqual

/** A delimited listing block (verbatim code). */
case class ListingBlock(delimiter: String, content: String)(val span: Span) extends Block derives CanEqual

/** A delimited sidebar block containing nested blocks. */
case class SidebarBlock(delimiter: String, blocks: List[Block])(val span: Span) extends Block derives CanEqual

/** A table block delimited by |===. */
case class TableBlock(rows: List[TableRow], delimiter: String)(val span: Span) extends Block derives CanEqual

/** A row in a table. */
case class TableRow(cells: List[TableCell])(val span: Span) extends AstNode derives CanEqual

/** A cell in a table row. */
case class TableCell(content: InlineContent)(val span: Span) extends AstNode derives CanEqual

/** A bullet list (items prefixed with "* "). */
case class UnorderedList(items: List[ListItem])(val span: Span) extends Block derives CanEqual

/** A numbered list (items prefixed with ". "). */
case class OrderedList(items: List[ListItem])(val span: Span) extends Block derives CanEqual

object Heading extends PosParserBridge2[Int, InlineContent, Heading]:
    def apply(level: Int, title: InlineContent)(span: Span): Heading = new Heading(level, title)(span)

object Paragraph extends PosParserBridge1[InlineContent, Paragraph]:
    def apply(content: InlineContent)(span: Span): Paragraph = new Paragraph(content)(span)

object UnorderedList extends PosParserBridge1[List[ListItem], UnorderedList]:
    def apply(items: List[ListItem])(span: Span): UnorderedList = new UnorderedList(items)(span)

object OrderedList extends PosParserBridge1[List[ListItem], OrderedList]:
    def apply(items: List[ListItem])(span: Span): OrderedList = new OrderedList(items)(span)

/** A document header with title and optional attributes. */
case class DocumentHeader(title: InlineContent, attributes: List[(String, String)])(val span: Span) extends AstNode
    derives CanEqual

/** The top-level document containing an ordered sequence of blocks, with optional header. */
case class Document(header: Option[DocumentHeader], blocks: List[Block])(val span: Span) extends AstNode
    derives CanEqual

object Document:
    /** Convenience constructor without header for backwards compatibility. */
    def apply(blocks: List[Block])(span: Span): Document = new Document(None, blocks)(span)
