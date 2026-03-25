package io.eleven19.ascribe.cst

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.AttributeList.{AttributeName, AttributeValue, OptionName, RoleName}
import io.eleven19.ascribe.parser.DocumentParser

/** Lowers a `CstDocument` to the AST `Document` representation.
  *
  * Transformations applied:
  *   - `CstBlankLine` and `CstLineComment` are dropped
  *   - `CstDelimitedBlock(Comment, ...)` is dropped
  *   - `CstParagraphLine` sequences are flattened into `Paragraph`
  *   - `CstBold(constrained=true)` → `ConstrainedBold`, `false` → `Bold`
  *   - `CstDelimitedBlock(kind, ...)` → corresponding AST block type
  *   - `CstList(Unordered/Ordered, ...)` → `UnorderedList`/`OrderedList`
  *   - `CstTable` → `Table`
  *   - Flat headings restructured into `Section` hierarchy via `restructure`
  *   - Body-level `CstAttributeEntry` dropped (no AST representation)
  *   - Unresolved `CstInclude` nodes cause an error
  */
object CstLowering:

    def toAst(cst: CstDocument): Document =
        val header = cst.header.map(lowerHeader)
        val blocks = cst.content
            .collect { case b: CstBlock => b }
            .flatMap(lowerBlock)
        val restructured = DocumentParser.restructure(blocks)
        Document(header, restructured)(cst.span)

    private def lowerHeader(h: CstDocumentHeader): DocumentHeader =
        DocumentHeader(
            title = lowerInlines(h.title.title),
            attributes = h.attributes.map(e => (e.name, e.value))
        )(h.span)

    private def lowerBlock(block: CstBlock): Option[Block] = block match
        case _: CstLineComment    => None
        case _: CstAttributeEntry => None
        case CstInclude(target, _) =>
            sys.error(s"Unresolved CstInclude: $target — resolve includes before lowering")

        case CstHeading(level, _, title) =>
            Some(Heading(level, lowerInlines(title))(block.span))

        case CstParagraph(lines) =>
            val content = lines.flatMap(line => lowerInlines(line.content))
            Some(Paragraph(content)(block.span))

        case CstDelimitedBlock(DelimitedBlockKind.Comment, _, _, _, _) =>
            None

        case CstDelimitedBlock(DelimitedBlockKind.Listing, delim, CstVerbatimContent(raw), attrs, title) =>
            Some(Listing(delim, raw, attrs.map(lowerAttrList), title.map(lowerBlockTitle))(block.span))

        case CstDelimitedBlock(DelimitedBlockKind.Literal, delim, CstVerbatimContent(raw), attrs, title) =>
            Some(Literal(delim, raw, attrs.map(lowerAttrList), title.map(lowerBlockTitle))(block.span))

        case CstDelimitedBlock(DelimitedBlockKind.Pass, delim, CstVerbatimContent(raw), attrs, title) =>
            Some(Pass(delim, raw, attrs.map(lowerAttrList), title.map(lowerBlockTitle))(block.span))

        case CstDelimitedBlock(DelimitedBlockKind.Sidebar, delim, CstNestedContent(children), attrs, title) =>
            val blocks = children.collect { case b: CstBlock => b }.flatMap(lowerBlock)
            Some(Sidebar(delim, blocks, attrs.map(lowerAttrList), title.map(lowerBlockTitle))(block.span))

        case CstDelimitedBlock(DelimitedBlockKind.Example, delim, CstNestedContent(children), attrs, title) =>
            val blocks = children.collect { case b: CstBlock => b }.flatMap(lowerBlock)
            Some(Example(delim, blocks, attrs.map(lowerAttrList), title.map(lowerBlockTitle))(block.span))

        case CstDelimitedBlock(DelimitedBlockKind.Quote, delim, CstNestedContent(children), attrs, title) =>
            val blocks = children.collect { case b: CstBlock => b }.flatMap(lowerBlock)
            Some(Quote(delim, blocks, attrs.map(lowerAttrList), title.map(lowerBlockTitle))(block.span))

        case CstDelimitedBlock(DelimitedBlockKind.Open, delim, CstNestedContent(children), attrs, title) =>
            val blocks = children.collect { case b: CstBlock => b }.flatMap(lowerBlock)
            Some(Open(delim, blocks, attrs.map(lowerAttrList), title.map(lowerBlockTitle))(block.span))

        case CstDelimitedBlock(_, delim, _, _, _) =>
            sys.error(s"Unexpected CstDelimitedBlock combination: delim=$delim")

        case CstList(ListVariant.Unordered, items) =>
            Some(UnorderedList(items.map(lowerListItem))(block.span))

        case CstList(ListVariant.Ordered, items) =>
            Some(OrderedList(items.map(lowerListItem))(block.span))

        case CstTable(rows, delim, format, attrs, title, hasBlank) =>
            Some(
                Table(
                    rows.map(lowerTableRow),
                    delim,
                    format,
                    attrs.map(lowerAttrList),
                    title.map(lowerBlockTitle),
                    hasBlank
                )(block.span)
            )

    private def lowerListItem(item: CstListItem): ListItem =
        ListItem(lowerInlines(item.content))(item.span)

    private def lowerTableRow(row: CstTableRow): TableRow =
        TableRow(row.cells.map(lowerTableCell))(row.span)

    private def lowerTableCell(cell: CstTableCell): TableCell =
        val content = cell.content match
            case CstCellInlines(inlines) => CellContent.Inlines(lowerInlines(inlines))
            case CstCellBlocks(topLevels) =>
                val blocks = topLevels.collect { case b: CstBlock => b }.flatMap(lowerBlock)
                CellContent.Blocks(blocks)
        TableCell(
            content,
            cell.style.map(s => CellSpecifier.StyleOperator(s.charAt(0))),
            cell.colSpan.map(CellSpecifier.ColSpanFactor(_)),
            cell.rowSpan.map(CellSpecifier.RowSpanFactor(_)),
            cell.dupFactor.map(CellSpecifier.DupFactor(_))
        )(cell.span)

    private def lowerAttrList(al: CstAttributeList): AttributeList =
        AttributeList(
            positional = al.positional.map(AttributeValue(_)),
            named = al.named.map((k, v) => (AttributeName(k), AttributeValue(v))),
            options = al.options.map(OptionName(_)),
            roles = al.roles.map(RoleName(_))
        )(al.span)

    private def lowerBlockTitle(bt: CstBlockTitle): Title =
        Title(lowerInlines(bt.content))(bt.span)

    private[cst] def lowerInlines(inlines: List[CstInline]): List[Inline] =
        inlines.map(lowerInline)

    private def lowerInline(inline: CstInline): Inline = inline match
        case CstText(content)        => Text(content)(inline.span)
        case CstBold(content, false) => Bold(lowerInlines(content))(inline.span)
        case CstBold(content, true)  => ConstrainedBold(lowerInlines(content))(inline.span)
        case CstItalic(content)      => Italic(lowerInlines(content))(inline.span)
        case CstMono(content)        => Mono(lowerInlines(content))(inline.span)
