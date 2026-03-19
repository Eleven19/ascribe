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

/** A delimited listing block (verbatim code). May have attributes like `[source,ruby]`. */
case class ListingBlock(
    delimiter: String,
    content: String,
    attributes: Option[AttributeList] = None,
    title: Option[BlockTitle] = None
)(val span: Span)
    extends Block derives CanEqual

/** A delimited sidebar block containing nested blocks. */
case class SidebarBlock(delimiter: String, blocks: List[Block])(val span: Span) extends Block derives CanEqual

object AttributeList:
    opaque type AttributeName  = String
    opaque type AttributeValue = String
    opaque type OptionName     = String
    opaque type RoleName       = String

    object AttributeName:
        def apply(s: String): AttributeName            = s
        extension (n: AttributeName) def value: String = n

    object AttributeValue:
        def apply(s: String): AttributeValue            = s
        extension (v: AttributeValue) def value: String = v

    object OptionName:
        def apply(s: String): OptionName            = s
        extension (o: OptionName) def value: String = o

    object RoleName:
        def apply(s: String): RoleName            = s
        extension (r: RoleName) def value: String = r

    def merge(a: AttributeList, b: AttributeList)(span: Span): AttributeList =
        AttributeList(
            positional = a.positional ++ b.positional,
            named = a.named ++ b.named,
            options = a.options ++ b.options,
            roles = a.roles ++ b.roles
        )(span)

case class AttributeList(
    positional: List[AttributeList.AttributeValue],
    named: Map[AttributeList.AttributeName, AttributeList.AttributeValue],
    options: List[AttributeList.OptionName],
    roles: List[AttributeList.RoleName]
)(val span: Span)
    extends AstNode derives CanEqual

case class BlockTitle(content: InlineContent)(val span: Span) extends AstNode derives CanEqual

/** Table data format. */
enum TableFormat:
    case PSV, CSV, DSV, TSV

/** A table block. The format determines cell parsing rules and default separator. */
case class TableBlock(
    rows: List[TableRow],
    delimiter: String,
    format: TableFormat = TableFormat.PSV,
    attributes: Option[AttributeList] = None,
    title: Option[BlockTitle] = None,
    hasBlankAfterFirstRow: Boolean = false
)(val span: Span)
    extends Block derives CanEqual

/** A row in a table. */
case class TableRow(cells: List[TableCell])(val span: Span) extends AstNode derives CanEqual

/** Cell specifier components parsed from the prefix before `|`. */
object CellSpecifier:
    opaque type StyleOperator = Char
    opaque type ColSpanFactor = Int
    opaque type RowSpanFactor = Int
    opaque type DupFactor     = Int

    object StyleOperator:
        def apply(c: Char): StyleOperator            = c
        extension (s: StyleOperator) def value: Char = s

    object ColSpanFactor:
        def apply(n: Int): ColSpanFactor            = n
        extension (c: ColSpanFactor) def value: Int = c

    object RowSpanFactor:
        def apply(n: Int): RowSpanFactor            = n
        extension (r: RowSpanFactor) def value: Int = r

    object DupFactor:
        def apply(n: Int): DupFactor            = n
        extension (d: DupFactor) def value: Int = d

/** Cell content: either inline elements (default/d style) or block elements (a style with nested documents). */
enum CellContent:
    case Inlines(content: InlineContent)
    case Blocks(blocks: scala.List[Block])

/** A cell in a table row with optional specifier components (style, spanning, duplication). */
case class TableCell(
    content: CellContent,
    style: Option[CellSpecifier.StyleOperator] = None,
    colSpan: Option[CellSpecifier.ColSpanFactor] = None,
    rowSpan: Option[CellSpecifier.RowSpanFactor] = None,
    dupFactor: Option[CellSpecifier.DupFactor] = None
)(val span: Span)
    extends AstNode derives CanEqual

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
