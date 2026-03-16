package io.github.eleven19.ascribe.ast

/** A list of inline elements forming the content of a single line. */
type InlineContent = List[Inline]

/** An inline element within a paragraph, heading, or list item. */
sealed trait Inline:
  def span: Span

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

object Text extends PosParserBridge1[String, Text]:
  def apply(content: String)(span: Span): Text = new Text(content)(span)

object Bold extends PosParserBridge1[List[Inline], Bold]:
  def apply(content: List[Inline])(span: Span): Bold = new Bold(content)(span)

object Italic extends PosParserBridge1[List[Inline], Italic]:
  def apply(content: List[Inline])(span: Span): Italic = new Italic(content)(span)

object Mono extends PosParserBridge1[List[Inline], Mono]:
  def apply(content: List[Inline])(span: Span): Mono = new Mono(content)(span)

/** A single item in a list block. */
case class ListItem(content: InlineContent)(val span: Span) derives CanEqual

object ListItem extends PosParserBridge1[InlineContent, ListItem]:
  def apply(content: InlineContent)(span: Span): ListItem = new ListItem(content)(span)

/** A block-level element in an AsciiDoc document. */
sealed trait Block:
  def span: Span

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

/** A paragraph of one or more lines of inline content. */
case class Paragraph(content: InlineContent)(val span: Span) extends Block derives CanEqual

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

/** The top-level document containing an ordered sequence of blocks. */
case class Document(blocks: List[Block])(val span: Span) derives CanEqual

object Document extends PosParserBridge1[List[Block], Document]:
  def apply(blocks: List[Block])(span: Span): Document = new Document(blocks)(span)
