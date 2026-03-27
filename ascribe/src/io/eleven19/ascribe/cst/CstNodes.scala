package io.eleven19.ascribe.cst

import io.eleven19.ascribe.ast.{Span, TableFormat}

// ── Base ─────────────────────────────────────────────────────────────────────

sealed trait CstNode:
    def span: Span

object CstNode:
    given CanEqual[CstNode, CstNode] = CanEqual.derived

// ── Top-level ─────────────────────────────────────────────────────────────────

/** A top-level content node is either a block or a blank line. */
sealed trait CstTopLevel extends CstNode

case class CstDocument(
    header: Option[CstDocumentHeader],
    content: List[CstTopLevel]
)(val span: Span)
    extends CstNode derives CanEqual

case class CstDocumentHeader(
    title: CstHeading,
    attributes: List[CstAttributeEntry]
)(val span: Span)
    extends CstNode derives CanEqual

// ── Block-level ───────────────────────────────────────────────────────────────

sealed trait CstBlock extends CstTopLevel

case class CstHeading(
    level: Int,
    marker: String,
    title: List[CstInline]
)(val span: Span)
    extends CstBlock derives CanEqual

case class CstParagraph(
    lines: List[CstParagraphLine]
)(val span: Span)
    extends CstBlock derives CanEqual

case class CstParagraphLine(
    content: List[CstInline]
)(val span: Span)
    extends CstNode derives CanEqual

case class CstDelimitedBlock(
    kind: DelimitedBlockKind,
    delimiter: String,
    content: CstBlockContent,
    attributes: Option[CstAttributeList],
    title: Option[CstBlockTitle]
)(val span: Span)
    extends CstBlock derives CanEqual

enum DelimitedBlockKind derives CanEqual:
    case Listing, Literal, Sidebar, Example, Quote, Open, Pass, Comment

// Note: CstBlockContent and CstCellContent extend CstNode rather than being
// standalone traits as in the spec. This is an intentional improvement: it
// allows these subtypes to be used uniformly wherever CstNode is expected
// (e.g. in CstVisitor dispatch), without requiring a separate trait hierarchy.
sealed trait CstBlockContent extends CstNode

case class CstVerbatimContent(raw: String)(val span: Span)               extends CstBlockContent derives CanEqual
case class CstNestedContent(children: List[CstTopLevel])(val span: Span) extends CstBlockContent derives CanEqual

case class CstList(
    variant: ListVariant,
    items: List[CstListItem]
)(val span: Span)
    extends CstBlock derives CanEqual

enum ListVariant derives CanEqual:
    case Unordered, Ordered

case class CstListItem(
    marker: String,
    content: List[CstInline]
)(val span: Span)
    extends CstNode derives CanEqual

case class CstTable(
    rows: List[CstTableRow],
    delimiter: String,
    format: TableFormat,
    attributes: Option[CstAttributeList],
    title: Option[CstBlockTitle],
    hasBlankAfterFirstRow: Boolean
)(val span: Span)
    extends CstBlock derives CanEqual

/** Include directive — first-class CST node. */
case class CstInclude(
    target: String,
    attributes: CstAttributeList
)(val span: Span)
    extends CstBlock derives CanEqual

/** Single-line comment: `// content` */
case class CstLineComment(
    content: String
)(val span: Span)
    extends CstBlock derives CanEqual

/** Attribute entry: `:name: value` or `:!name:` (unset form).
  *
  * When `unset = true` the entry represents `:!name:` and `value` is always empty. When `unset = false` the entry
  * represents `:name: value` where `value` is the raw string after the trailing `:`.
  */
case class CstAttributeEntry(
    name: String,
    value: String,
    unset: Boolean
)(val span: Span)
    extends CstBlock derives CanEqual

/** Paragraph-form admonition: `NOTE: text on same line` */
case class CstAdmonitionParagraph(
    kind: String,
    content: List[CstInline]
)(val span: Span)
    extends CstBlock derives CanEqual

/** Blank line — preserved as a node instead of consumed. */
case class CstBlankLine()(val span: Span) extends CstTopLevel derives CanEqual

// ── Block metadata ────────────────────────────────────────────────────────────

case class CstAttributeList(
    positional: List[String],
    named: Map[String, String],
    options: List[String],
    roles: List[String]
)(val span: Span)
    extends CstNode derives CanEqual

object CstAttributeList:
    val empty: Span => CstAttributeList = span => CstAttributeList(Nil, Map.empty, Nil, Nil)(span)

case class CstBlockTitle(
    content: List[CstInline]
)(val span: Span)
    extends CstNode derives CanEqual

// ── Inline nodes ──────────────────────────────────────────────────────────────

sealed trait CstInline extends CstNode

case class CstText(content: String)(val span: Span) extends CstInline derives CanEqual

case class CstBold(
    content: List[CstInline],
    constrained: Boolean
)(val span: Span)
    extends CstInline derives CanEqual

// TODO: Add `constrained: Boolean` when constrained italic (`_text_`) is added
// to the parser. Currently only unconstrained italic (`__text__`) is supported.
// See spec Known Limitations.
case class CstItalic(
    content: List[CstInline]
)(val span: Span)
    extends CstInline derives CanEqual

// TODO: Add `constrained: Boolean` when constrained monospace (`` `text` ``) is
// added to the parser. Currently only unconstrained mono (` ``text`` `) is
// supported. See spec Known Limitations.
case class CstMono(
    content: List[CstInline]
)(val span: Span)
    extends CstInline derives CanEqual

case class CstAttributeRef(name: String)(val span: Span) extends CstInline derives CanEqual

sealed trait CstLink extends CstInline:
    def target: String

case class CstAutolink(target: String)(val span: Span) extends CstLink derives CanEqual

case class CstUrlMacro(target: String, text: List[CstInline])(val span: Span)
    extends CstLink derives CanEqual

case class CstLinkMacro(target: String, text: List[CstInline])(val span: Span)
    extends CstLink derives CanEqual

case class CstMailtoMacro(target: String, text: List[CstInline])(val span: Span)
    extends CstLink derives CanEqual

// ── Table sub-nodes ───────────────────────────────────────────────────────────

case class CstTableRow(
    cells: List[CstTableCell]
)(val span: Span)
    extends CstNode derives CanEqual

case class CstTableCell(
    content: CstCellContent,
    style: Option[String],
    colSpan: Option[Int],
    rowSpan: Option[Int],
    dupFactor: Option[Int]
)(val span: Span)
    extends CstNode derives CanEqual

sealed trait CstCellContent extends CstNode

case class CstCellInlines(content: List[CstInline])(val span: Span)  extends CstCellContent derives CanEqual
case class CstCellBlocks(content: List[CstTopLevel])(val span: Span) extends CstCellContent derives CanEqual
