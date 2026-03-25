package io.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.{atomic, many}
import parsley.character.string
import parsley.combinator.manyTill
import parsley.errors.combinator.ErrorMethods
import parsley.position.pos

import io.eleven19.ascribe.ast.{Bold, ConstrainedBold, Inline, InlineContent, Italic, Mono, Span, Text, mkSpan}
import io.eleven19.ascribe.lexer.AsciiDocLexer.*

/** Parsers for inline AsciiDoc elements.
  *
  * Inline content sits inside headings, paragraphs, and list items. This module handles unconstrained markup spans
  * (double-delimiter pairs) and falls back to plain text for everything else.
  *
  * ==Supported markup==
  *   - `**bold**` -- [[Bold]]
  *   - `__italic__` -- [[Italic]]
  *   - ` ``mono`` ` -- [[Mono]]
  *   - everything else -- [[Text]]
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
      *
      * @param open
      *   opening delimiter string (e.g. `"**"`)
      * @param close
      *   closing delimiter string; usually identical to `open`
      */
    private def delimitedContent(open: String, close: String): Parsley[String] =
        atomic(string(open) *> manyTill(nonEolChar, string(close))).map(_.mkString)

    // -----------------------------------------------------------------------
    // Inline element parsers
    // -----------------------------------------------------------------------

    /** Parses an unconstrained **bold** span: `**content**`.
      *
      * Uses [[atomic]] to guarantee that a lone `*` or an unclosed `**` does not consume input and instead falls
      * through to [[unpairedMarkupInline]]. Uses manual pos captures since delimitedContent returns String, not
      * List[Inline].
      */
    val boldSpan: Parsley[Inline] =
        (pos <~> delimitedContent("**", "**") <~> pos)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                Bold(List(Text(content)(span)))(span)
            }
            .label("bold span")
            .explain("Bold text is surrounded by double asterisks, e.g. **bold**")

    /** Parses an unconstrained _italic_ span: `__content__`. */
    val italicSpan: Parsley[Inline] =
        (pos <~> delimitedContent("__", "__") <~> pos)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                Italic(List(Text(content)(span)))(span)
            }
            .label("italic span")
            .explain("Italic text is surrounded by double underscores, e.g. __italic__")

    /** Parses an unconstrained `monospace` span: ` ``content`` `. */
    val monoSpan: Parsley[Inline] =
        (pos <~> delimitedContent("``", "``") <~> pos)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                Mono(List(Text(content)(span)))(span)
            }
            .label("monospace span")
            .explain("Monospace text is surrounded by double backticks, e.g. ``mono``")

    /** Parses a constrained *bold* span: `*content*`.
      *
      * Uses single asterisks. Must be tried after unconstrained `**` to avoid ambiguity.
      */
    val constrainedBoldSpan: Parsley[Inline] =
        (pos <~> delimitedContent("*", "*") <~> pos)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                ConstrainedBold(List(Text(content)(span)))(span)
            }
            .label("constrained bold span")

    /** Parses one or more unadorned prose characters as a [[Text]] node.
      *
      * Stops at any inline markup delimiter (`*`, `_`, `` ` ``) or newline so that [[boldSpan]], [[italicSpan]], and
      * [[monoSpan]] get first priority.
      */
    val plainTextInline: Parsley[Inline] =
        Text(plainText)
            .label("text")

    /** Fallback for a single markup character that did not open a valid span.
      *
      * For example, a lone `*` in prose (not followed by another `*` to form `**`) is consumed here as a [[Text]] of
      * length 1.
      */
    val unpairedMarkupInline: Parsley[Inline] =
        (pos <~> unpairedMarkupChar <~> pos).map { case ((s, c), e) =>
            Text(c.toString)(mkSpan(s, e))
        }.hide

    /** Parses a single inline element (one of the above parsers in priority order). Unconstrained (`**`) is tried
      * before constrained (`*`) to avoid ambiguity.
      */
    val inlineElement: Parsley[Inline] =
        boldSpan | constrainedBoldSpan | italicSpan | monoSpan | plainTextInline | unpairedMarkupInline

    /** Parses zero or more inline elements, stopping naturally at a newline or end-of-input. */
    val lineContent: Parsley[InlineContent] = many(inlineElement)
