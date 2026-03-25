package io.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.{atomic, many}
import parsley.character.string
import parsley.combinator.manyTill
import parsley.errors.combinator.ErrorMethods
import parsley.position.pos

import io.eleven19.ascribe.ast.{Span, mkSpan}
import io.eleven19.ascribe.cst.{CstBold, CstInline, CstItalic, CstMono, CstText}
import io.eleven19.ascribe.lexer.AsciiDocLexer.*

/** Parsers for inline AsciiDoc elements.
  *
  * Inline content sits inside headings, paragraphs, and list items. This module handles unconstrained markup spans
  * (double-delimiter pairs) and falls back to plain text for everything else. Each parser emits a [[CstInline]] node.
  *
  * ==Supported markup==
  *   - `**bold**` -- [[CstBold]] (constrained=false)
  *   - `*bold*` -- [[CstBold]] (constrained=true)
  *   - `__italic__` -- [[CstItalic]]
  *   - ` ``mono`` ` -- [[CstMono]]
  *   - everything else -- [[CstText]]
  *
  * ==Error handling==
  * Every parser carries a `.label` for "expected" messages and, where helpful, an `.explain` that describes the correct
  * syntax to the user.
  */
object InlineParser:

    // -----------------------------------------------------------------------
    // Delimited span helpers
    // -----------------------------------------------------------------------

    /** Parses a delimited inline span.
      *
      * Consumes the `open` delimiter, then all [[nonEolChar]] characters until the `close` delimiter is consumed,
      * returning the collected characters as a `String`. The entire parser is wrapped in [[atomic]] so that a missing
      * closing delimiter causes clean backtracking to before the opening delimiter.
      */
    private def delimitedContent(open: String, close: String): Parsley[String] =
        atomic(string(open) *> manyTill(nonEolChar, string(close))).map(_.mkString)

    // -----------------------------------------------------------------------
    // Inline element parsers
    // -----------------------------------------------------------------------

    /** Parses an unconstrained **bold** span: `**content**`. */
    val boldSpan: Parsley[CstInline] =
        (pos <~> delimitedContent("**", "**") <~> pos)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                CstBold(List(CstText(content)(span)), constrained = false)(span)
            }
            .label("bold span")
            .explain("Bold text is surrounded by double asterisks, e.g. **bold**")

    /** Parses an unconstrained _italic_ span: `__content__`. */
    val italicSpan: Parsley[CstInline] =
        (pos <~> delimitedContent("__", "__") <~> pos)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                CstItalic(List(CstText(content)(span)))(span)
            }
            .label("italic span")
            .explain("Italic text is surrounded by double underscores, e.g. __italic__")

    /** Parses an unconstrained `monospace` span: ` ``content`` `. */
    val monoSpan: Parsley[CstInline] =
        (pos <~> delimitedContent("``", "``") <~> pos)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                CstMono(List(CstText(content)(span)))(span)
            }
            .label("monospace span")
            .explain("Monospace text is surrounded by double backticks, e.g. ``mono``")

    /** Parses a constrained *bold* span: `*content*`.
      *
      * Must be tried after unconstrained `**` to avoid ambiguity.
      */
    val constrainedBoldSpan: Parsley[CstInline] =
        (pos <~> delimitedContent("*", "*") <~> pos)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                CstBold(List(CstText(content)(span)), constrained = true)(span)
            }
            .label("constrained bold span")

    /** Parses one or more unadorned prose characters as a [[CstText]] node. */
    val plainTextInline: Parsley[CstInline] =
        (pos <~> plainText <~> pos)
            .map { case ((s, content), e) => CstText(content)(mkSpan(s, e)) }
            .label("text")

    /** Fallback for a single markup character that did not open a valid span. */
    val unpairedMarkupInline: Parsley[CstInline] =
        (pos <~> unpairedMarkupChar <~> pos).map { case ((s, c), e) => CstText(c.toString)(mkSpan(s, e)) }.hide

    /** Parses a single inline element (one of the above parsers in priority order). Unconstrained (`**`) is tried
      * before constrained (`*`) to avoid ambiguity.
      */
    val inlineElement: Parsley[CstInline] =
        boldSpan | constrainedBoldSpan | italicSpan | monoSpan | plainTextInline | unpairedMarkupInline

    /** Parses zero or more inline elements, stopping naturally at a newline or end-of-input. */
    val lineContent: Parsley[List[CstInline]] = many(inlineElement)
