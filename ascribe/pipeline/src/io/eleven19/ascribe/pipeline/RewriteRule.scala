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

    /** Combine multiple rules. Rules are applied in order; the first non-Retain result wins. */
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
                val headResult = rewriteBlock(head, rule)
                val tailResult = rewriteBlocks(tail, rule)
                headResult.map { maybeHead =>
                    tailResult.map { rest =>
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
                val newTitle  = rewriteInlines(title, rule)
                val newBlocks = rewriteBlocks(blocks, rule)
                newTitle.map(t => newBlocks.map(b => Section(level, t, b)(s.span)))

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

            // Verbatim blocks have no child AST nodes to rewrite
            case other => other

    private def rewriteListItems[S](items: List[ListItem], rule: RewriteRule[S]): List[ListItem] < S =
        items match
            case Nil => List.empty[ListItem]
            case head :: tail =>
                val headResult = rewriteInlines(head.content, rule).map(ListItem(_)(head.span))
                val tailResult = rewriteListItems(tail, rule)
                headResult.map(h => tailResult.map(t => h :: t))

    private def rewriteInlines[S](inlines: List[Inline], rule: RewriteRule[S]): List[Inline] < S =
        inlines match
            case Nil => List.empty[Inline]
            case head :: tail =>
                val headResult = rewriteInline(head, rule)
                val tailResult = rewriteInlines(tail, rule)
                headResult.map { maybeHead =>
                    tailResult.map { rest =>
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
