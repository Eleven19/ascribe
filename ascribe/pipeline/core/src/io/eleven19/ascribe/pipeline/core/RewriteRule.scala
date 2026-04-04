package io.eleven19.ascribe.pipeline.core

import io.eleven19.ascribe.ast.*

enum RewriteAction[+A]:
    case Replace(node: A)
    case Remove
    case Retain

object RewriteAction:
    given [A, B]: CanEqual[RewriteAction[A], RewriteAction[B]] = CanEqual.derived

trait RewriteRule:
    def applyBlock(block: Block): RewriteAction[Block]     = RewriteAction.Retain
    def applyInline(inline: Inline): RewriteAction[Inline] = RewriteAction.Retain

object RewriteRule:

    def forBlocks(pf: PartialFunction[Block, RewriteAction[Block]]): RewriteRule =
        new RewriteRule:
            override def applyBlock(block: Block): RewriteAction[Block] =
                if pf.isDefinedAt(block) then pf(block) else RewriteAction.Retain

    def forInlines(pf: PartialFunction[Inline, RewriteAction[Inline]]): RewriteRule =
        new RewriteRule:
            override def applyInline(inline: Inline): RewriteAction[Inline] =
                if pf.isDefinedAt(inline) then pf(inline) else RewriteAction.Retain

    def compose(rules: RewriteRule*): RewriteRule =
        new RewriteRule:
            override def applyBlock(block: Block): RewriteAction[Block] =
                applyFirst(rules.toList, _.applyBlock(block))
            override def applyInline(inline: Inline): RewriteAction[Inline] =
                applyFirst(rules.toList, _.applyInline(inline))

    private def applyFirst[A](rules: List[RewriteRule], apply: RewriteRule => RewriteAction[A]): RewriteAction[A] =
        rules match
            case Nil => RewriteAction.Retain
            case rule :: rest =>
                apply(rule) match
                    case RewriteAction.Retain => applyFirst(rest, apply)
                    case other                => other

    def rewrite(document: Document, rule: RewriteRule): Document =
        Document(document.header, rewriteBlocks(document.blocks, rule))(document.span)

    private def rewriteBlocks(blocks: List[Block], rule: RewriteRule): List[Block] =
        blocks.flatMap(b => rewriteBlock(b, rule).toList)

    private def rewriteBlock(block: Block, rule: RewriteRule): Option[Block] =
        rule.applyBlock(block) match
            case RewriteAction.Remove     => None
            case RewriteAction.Replace(b) => rewriteBlockChildren(b, rule)
            case RewriteAction.Retain     => rewriteBlockChildren(block, rule)

    private def rewriteBlockChildren(block: Block, rule: RewriteRule): Option[Block] =
        block match
            case s @ Section(level, title, blocks) =>
                Some(Section(level, rewriteInlines(title, rule), rewriteBlocks(blocks, rule))(s.span))
            case p @ Paragraph(content, attrs, title) =>
                Some(Paragraph(rewriteInlines(content, rule), attrs, title)(p.span))
            case sb @ Sidebar(delim, blocks, attrs, title) =>
                Some(Sidebar(delim, rewriteBlocks(blocks, rule), attrs, title)(sb.span))
            case ex @ Example(delim, blocks, attrs, title) =>
                Some(Example(delim, rewriteBlocks(blocks, rule), attrs, title)(ex.span))
            case qt @ Quote(delim, blocks, attrs, title) =>
                Some(Quote(delim, rewriteBlocks(blocks, rule), attrs, title)(qt.span))
            case op @ Open(delim, blocks, attrs, title) =>
                Some(Open(delim, rewriteBlocks(blocks, rule), attrs, title)(op.span))
            case ul @ UnorderedList(items) =>
                rewriteListItems(items, rule).map(UnorderedList(_)(ul.span))
            case ol @ OrderedList(items) =>
                rewriteListItems(items, rule).map(OrderedList(_)(ol.span))
            case h @ Heading(level, title) =>
                Some(Heading(level, rewriteInlines(title, rule))(h.span))
            case tb @ Table(rows, delim, fmt, attrs, title, blank) =>
                rewriteTableRows(rows, rule).map(Table(_, delim, fmt, attrs, title, blank)(tb.span))
            case other => Some(other)

    private def rewriteListItems(items: List[ListItem], rule: RewriteRule): Option[List[ListItem]] =
        items match
            case Nil => Some(Nil)
            case head :: tail =>
                rewriteListItems(tail, rule).map { t =>
                    ListItem(rewriteInlines(head.content, rule))(head.span) :: t
                }

    private def rewriteInlines(inlines: List[Inline], rule: RewriteRule): List[Inline] =
        inlines.flatMap(i => rewriteInline(i, rule).toList)

    private def rewriteInline(inline: Inline, rule: RewriteRule): Option[Inline] =
        rule.applyInline(inline) match
            case RewriteAction.Remove     => None
            case RewriteAction.Replace(i) => Some(rewriteInlineChildren(i, rule))
            case RewriteAction.Retain     => Some(rewriteInlineChildren(inline, rule))

    private def rewriteInlineChildren(inline: Inline, rule: RewriteRule): Inline =
        inline match
            case b @ Bold(content) =>
                Bold(rewriteInlines(content, rule))(b.span)
            case cb @ ConstrainedBold(content) =>
                ConstrainedBold(rewriteInlines(content, rule))(cb.span)
            case i @ Italic(content) =>
                Italic(rewriteInlines(content, rule))(i.span)
            case m @ Mono(content) =>
                Mono(rewriteInlines(content, rule))(m.span)
            case other => other

    private def rewriteTableRows(rows: List[TableRow], rule: RewriteRule): Option[List[TableRow]] =
        rows match
            case Nil => Some(Nil)
            case head :: tail =>
                rewriteTableRow(head, rule).flatMap { h =>
                    rewriteTableRows(tail, rule).map(t => h :: t)
                }

    private def rewriteTableRow(row: TableRow, rule: RewriteRule): Option[TableRow] =
        rewriteTableCells(row.cells, rule).map(TableRow(_)(row.span))

    private def rewriteTableCells(cells: List[TableCell], rule: RewriteRule): Option[List[TableCell]] =
        cells match
            case Nil => Some(Nil)
            case head :: tail =>
                rewriteTableCell(head, rule).flatMap { h =>
                    rewriteTableCells(tail, rule).map(t => h :: t)
                }

    private def rewriteTableCell(cell: TableCell, rule: RewriteRule): Option[TableCell] =
        cell.content match
            case CellContent.Inlines(inlines) =>
                Some(
                    TableCell(
                        CellContent.Inlines(rewriteInlines(inlines, rule)),
                        cell.style,
                        cell.colSpan,
                        cell.rowSpan,
                        cell.dupFactor
                    )(cell.span)
                )
            case CellContent.Blocks(blocks) =>
                Some(
                    TableCell(
                        CellContent.Blocks(rewriteBlocks(blocks, rule)),
                        cell.style,
                        cell.colSpan,
                        cell.rowSpan,
                        cell.dupFactor
                    )(cell.span)
                )
