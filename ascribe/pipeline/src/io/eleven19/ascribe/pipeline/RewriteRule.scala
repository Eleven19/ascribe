package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.ast.*
import kyo.*

/** The result of applying a rewrite rule to an AST node. */
enum RewriteAction[+A]:
    /** Replace the node with a new node. */
    case Replace(node: A)

    /** Remove the node from its parent. */
    case Remove

    /** Keep the node unchanged. */
    case Retain

object RewriteAction:
    given [A, B]: CanEqual[RewriteAction[A], RewriteAction[B]] = CanEqual.derived

/** A rule for rewriting AST nodes within a document.
  *
  * Rules are partial functions: they only match on specific node types. Unmatched nodes are retained unchanged. Rules
  * can carry Kyo effects in their effect set `S`.
  */
trait RewriteRule[S]:
    /** Apply the rule to a block node. Override to match specific block types. */
    def applyBlock(block: Block): RewriteAction[Block] < S = RewriteAction.Retain

    /** Apply the rule to an inline node. Override to match specific inline types. */
    def applyInline(inline: Inline): RewriteAction[Inline] < S = RewriteAction.Retain

object RewriteRule:

    /** Create a block-level rewrite rule from a partial function (pure). */
    def forBlocks(pf: PartialFunction[Block, RewriteAction[Block]]): RewriteRule[Any] =
        new RewriteRule[Any]:
            override def applyBlock(block: Block): RewriteAction[Block] < Any =
                if pf.isDefinedAt(block) then pf(block) else RewriteAction.Retain

    /** Create an inline-level rewrite rule from a partial function (pure). */
    def forInlines(pf: PartialFunction[Inline, RewriteAction[Inline]]): RewriteRule[Any] =
        new RewriteRule[Any]:
            override def applyInline(inline: Inline): RewriteAction[Inline] < Any =
                if pf.isDefinedAt(inline) then pf(inline) else RewriteAction.Retain

    /** Combine multiple rules. Rules are applied in order; the first non-Retain result wins. With zero rules, returns a
      * rule that retains everything.
      */
    def compose[S](rules: RewriteRule[S]*): RewriteRule[S] =
        new RewriteRule[S]:
            override def applyBlock(block: Block): RewriteAction[Block] < S =
                applyFirst(rules.toList, _.applyBlock(block))

            override def applyInline(inline: Inline): RewriteAction[Inline] < S =
                applyFirst(rules.toList, _.applyInline(inline))

    private def applyFirst[A, S](
        rules: List[RewriteRule[S]],
        apply: RewriteRule[S] => RewriteAction[A] < S
    ): RewriteAction[A] < S =
        rules match
            case Nil => RewriteAction.Retain
            case rule :: rest =>
                apply(rule).map {
                    case RewriteAction.Retain => applyFirst(rest, apply)
                    case other                => other
                }

    /** Apply a rewrite rule to a Document, traversing all blocks and inlines. */
    def rewrite[S](document: Document, rule: RewriteRule[S]): Document < S =
        rewriteBlocks(document.blocks, rule).map { newBlocks =>
            Document(document.header, newBlocks)(document.span)
        }

    private def rewriteBlocks[S](blocks: List[Block], rule: RewriteRule[S]): List[Block] < S =
        blocks match
            case Nil => List.empty[Block]
            case head :: tail =>
                rewriteBlock(head, rule).map { maybeHead =>
                    rewriteBlocks(tail, rule).map { rest =>
                        maybeHead.toList ++ rest
                    }
                }

    private def rewriteBlock[S](block: Block, rule: RewriteRule[S]): Option[Block] < S =
        rule.applyBlock(block).map {
            case RewriteAction.Remove     => None
            case RewriteAction.Replace(b) => rewriteBlockChildren(b, rule).map(Some(_))
            case RewriteAction.Retain     => rewriteBlockChildren(block, rule).map(Some(_))
        }

    private def rewriteBlockChildren[S](block: Block, rule: RewriteRule[S]): Block < S =
        block match
            case s @ Section(level, title, blocks) =>
                rewriteInlines(title, rule).map { t =>
                    rewriteBlocks(blocks, rule).map(b => Section(level, t, b)(s.span))
                }

            case p @ Paragraph(content) =>
                rewriteInlines(content, rule).map(Paragraph(_)(p.span))

            case sb @ Sidebar(delim, blocks, attrs, title) =>
                rewriteBlocks(blocks, rule).map(Sidebar(delim, _, attrs, title)(sb.span))

            case ex @ Example(delim, blocks, attrs, title) =>
                rewriteBlocks(blocks, rule).map(Example(delim, _, attrs, title)(ex.span))

            case qt @ Quote(delim, blocks, attrs, title) =>
                rewriteBlocks(blocks, rule).map(Quote(delim, _, attrs, title)(qt.span))

            case op @ Open(delim, blocks, attrs, title) =>
                rewriteBlocks(blocks, rule).map(Open(delim, _, attrs, title)(op.span))

            case ul @ UnorderedList(items) =>
                rewriteListItems(items, rule).map(UnorderedList(_)(ul.span))

            case ol @ OrderedList(items) =>
                rewriteListItems(items, rule).map(OrderedList(_)(ol.span))

            case h @ Heading(level, title) =>
                rewriteInlines(title, rule).map(Heading(level, _)(h.span))

            case tb @ Table(rows, delim, fmt, attrs, title, blank) =>
                rewriteTableRows(rows, rule).map(Table(_, delim, fmt, attrs, title, blank)(tb.span))

            // Verbatim blocks (Listing, Literal, Comment, Pass) have no child AST nodes to rewrite
            case other => other

    private def rewriteListItems[S](items: List[ListItem], rule: RewriteRule[S]): List[ListItem] < S =
        items match
            case Nil => List.empty[ListItem]
            case head :: tail =>
                rewriteInlines(head.content, rule).map(ListItem(_)(head.span)).map { h =>
                    rewriteListItems(tail, rule).map(t => h :: t)
                }

    private def rewriteInlines[S](inlines: List[Inline], rule: RewriteRule[S]): List[Inline] < S =
        inlines match
            case Nil => List.empty[Inline]
            case head :: tail =>
                rewriteInline(head, rule).map { maybeHead =>
                    rewriteInlines(tail, rule).map { rest =>
                        maybeHead.toList ++ rest
                    }
                }

    private def rewriteInline[S](inline: Inline, rule: RewriteRule[S]): Option[Inline] < S =
        rule.applyInline(inline).map {
            case RewriteAction.Remove     => None
            case RewriteAction.Replace(i) => rewriteInlineChildren(i, rule).map(Some(_))
            case RewriteAction.Retain     => rewriteInlineChildren(inline, rule).map(Some(_))
        }

    private def rewriteInlineChildren[S](inline: Inline, rule: RewriteRule[S]): Inline < S =
        inline match
            case b @ Bold(content) =>
                rewriteInlines(content, rule).map(Bold(_)(b.span))
            case cb @ ConstrainedBold(content) =>
                rewriteInlines(content, rule).map(ConstrainedBold(_)(cb.span))
            case i @ Italic(content) =>
                rewriteInlines(content, rule).map(Italic(_)(i.span))
            case m @ Mono(content) =>
                rewriteInlines(content, rule).map(Mono(_)(m.span))
            case other => other // Text has no children

    private def rewriteTableRows[S](rows: List[TableRow], rule: RewriteRule[S]): List[TableRow] < S =
        rows match
            case Nil => List.empty[TableRow]
            case head :: tail =>
                rewriteTableRow(head, rule).map { h =>
                    rewriteTableRows(tail, rule).map(t => h :: t)
                }

    private def rewriteTableRow[S](row: TableRow, rule: RewriteRule[S]): TableRow < S =
        rewriteTableCells(row.cells, rule).map(TableRow(_)(row.span))

    private def rewriteTableCells[S](cells: List[TableCell], rule: RewriteRule[S]): List[TableCell] < S =
        cells match
            case Nil => List.empty[TableCell]
            case head :: tail =>
                rewriteTableCell(head, rule).map { h =>
                    rewriteTableCells(tail, rule).map(t => h :: t)
                }

    private def rewriteTableCell[S](cell: TableCell, rule: RewriteRule[S]): TableCell < S =
        cell.content match
            case CellContent.Inlines(inlines) =>
                rewriteInlines(inlines, rule).map { newInlines =>
                    TableCell(CellContent.Inlines(newInlines), cell.style, cell.colSpan, cell.rowSpan, cell.dupFactor)(
                        cell.span
                    )
                }
            case CellContent.Blocks(blocks) =>
                rewriteBlocks(blocks, rule).map { newBlocks =>
                    TableCell(CellContent.Blocks(newBlocks), cell.style, cell.colSpan, cell.rowSpan, cell.dupFactor)(
                        cell.span
                    )
                }
