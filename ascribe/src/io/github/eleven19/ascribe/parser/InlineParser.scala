package io.github.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.atomic
import parsley.character.string
import parsley.combinator.{many, manyTill}
import parsley.errors.combinator.ErrorMethods

import io.github.eleven19.ascribe.ast.{Inline, InlineContent}
import io.github.eleven19.ascribe.lexer.AsciiDocLexer.*

/** Parsers for inline AsciiDoc elements.
  *
  * Inline content sits inside headings, paragraphs, and list items. This module handles unconstrained markup spans
  * (double-delimiter pairs) and falls back to plain text for everything else.
  *
  * ==Supported markup==
  *   - `**bold**` – [[Inline.Bold]]
  *   - `__italic__` – [[Inline.Italic]]
  *   - ` ``mono`` ` – [[Inline.Mono]]
  *   - everything else – [[Inline.Text]]
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
      * through to [[unpairedMarkupInline]].
      */
    val boldSpan: Parsley[Inline] =
        delimitedContent("**", "**")
            .map(s => Inline.Bold(List(Inline.Text(s))))
            .label("bold span")
            .explain("Bold text is surrounded by double asterisks, e.g. **bold**")

    /** Parses an unconstrained _italic_ span: `__content__`. */
    val italicSpan: Parsley[Inline] =
        delimitedContent("__", "__")
            .map(s => Inline.Italic(List(Inline.Text(s))))
            .label("italic span")
            .explain("Italic text is surrounded by double underscores, e.g. __italic__")

    /** Parses an unconstrained `monospace` span: ` ``content`` `. */
    val monoSpan: Parsley[Inline] =
        delimitedContent("``", "``")
            .map(s => Inline.Mono(List(Inline.Text(s))))
            .label("monospace span")
            .explain("Monospace text is surrounded by double backticks, e.g. ``mono``")

    /** Parses one or more unadorned prose characters as a [[Inline.Text]] node.
      *
      * Stops at any inline markup delimiter (`*`, `_`, `` ` ``) or newline so that [[boldSpan]], [[italicSpan]], and
      * [[monoSpan]] get first priority.
      */
    val plainTextInline: Parsley[Inline] =
        plainText
            .map(Inline.Text.apply)
            .label("text")

    /** Fallback for a single markup character that did not open a valid span.
      *
      * For example, a lone `*` in prose (not followed by another `*` to form `**`) is consumed here as a
      * [[Inline.Text]] of length 1.
      */
    val unpairedMarkupInline: Parsley[Inline] =
        unpairedMarkupChar
            .map(c => Inline.Text(c.toString))
            .hide // suppress from "expected" messages – it is a last resort

    /** Parses a single inline element (one of the above parsers in priority order). */
    val inlineElement: Parsley[Inline] =
        boldSpan | italicSpan | monoSpan | plainTextInline | unpairedMarkupInline

    /** Parses zero or more inline elements, stopping naturally at a newline or end-of-input. */
    val lineContent: Parsley[InlineContent] = many(inlineElement)
