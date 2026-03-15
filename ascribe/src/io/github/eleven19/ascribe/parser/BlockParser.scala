package io.github.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.{atomic, notFollowedBy}
import parsley.character.{char, string}
import parsley.combinator.some
import parsley.errors.combinator.ErrorMethods

import io.github.eleven19.ascribe.ast.{Block, InlineContent, ListItem}
import io.github.eleven19.ascribe.lexer.AsciiDocLexer.*
import io.github.eleven19.ascribe.parser.InlineParser.*

/** Parsers for block-level AsciiDoc elements.
  *
  * Each parser recognises exactly one kind of block and is meant to be composed by
  * [[DocumentParser]]. Parsers that start with a distinctive prefix (heading markers, list
  * markers) are wrapped in [[atomic]] so that failures produce clean backtracking.
  *
  * ==Supported blocks==
  *   - `= Title` through `===== Title` – [[heading]]
  *   - `* item` – [[unorderedList]]
  *   - `. item` – [[orderedList]]
  *   - everything else – [[paragraph]]
  */
object BlockParser:

    // -----------------------------------------------------------------------
    // Headings
    // -----------------------------------------------------------------------

    /** Parses the leading `=`-markers of a heading and returns the heading level (1–5).
      *
      * Tries longer sequences first to avoid the two-character `==` matching before `===`.
      */
    private val headingLevel: Parsley[Int] =
        atomic(string("=====")).as(5) |
            atomic(string("====")).as(4) |
            atomic(string("===")).as(3) |
            atomic(string("==")).as(2) |
            atomic(string("=")).as(1)

    /** Parses a section heading.
      *
      * Syntax: one to five `=` characters, a single space, then the title on the rest of the
      * line.  Level is determined by the number of `=` signs.
      *
      * {{{
      * = Document title
      * == Chapter
      * === Section
      * }}}
      */
    val heading: Parsley[Block] =
        atomic(
            for
                level <- headingLevel
                _     <- char(' ')
                title <- lineContent
                _     <- eolOrEof
            yield Block.Heading(level, title)
        )
            .label("heading")
            .explain(
                "A heading starts with one to five equals signs followed by a space, e.g. = Title"
            )

    // -----------------------------------------------------------------------
    // Lists
    // -----------------------------------------------------------------------

    /** Parses a single unordered list item line: `* content`. */
    private val unorderedItem: Parsley[ListItem] =
        atomic(
            for
                _ <- char('*') *> char(' ')
                c <- lineContent
                _ <- eolOrEof
            yield ListItem(c)
        ).label("unordered list item")

    /** Parses one or more consecutive `* item` lines as an [[Block.UnorderedList]]. */
    val unorderedList: Parsley[Block] =
        some(unorderedItem)
            .map(items => Block.UnorderedList(items.toList))
            .label("unordered list")

    /** Parses a single ordered list item line: `. content`. */
    private val orderedItem: Parsley[ListItem] =
        atomic(
            for
                _ <- char('.') *> char(' ')
                c <- lineContent
                _ <- eolOrEof
            yield ListItem(c)
        ).label("ordered list item")

    /** Parses one or more consecutive `. item` lines as an [[Block.OrderedList]]. */
    val orderedList: Parsley[Block] =
        some(orderedItem)
            .map(items => Block.OrderedList(items.toList))
            .label("ordered list")

    // -----------------------------------------------------------------------
    // Paragraphs
    // -----------------------------------------------------------------------

    /** Negative lookahead for any block-starting prefix.
      *
      * Prevents [[paragraphLine]] from consuming lines that belong to a heading or list.
      */
    private val notBlockStart: Parsley[Unit] =
        notFollowedBy(headingLevel *> char(' ')) *>
            notFollowedBy(char('*') *> char(' ')) *>
            notFollowedBy(char('.') *> char(' '))

    /** Parses a single non-empty, non-block-start line as a list of inline elements. */
    private val paragraphLine: Parsley[InlineContent] =
        (notBlockStart *> some(inlineElement) <* eolOrEof).label("paragraph line")

    /** Parses one or more consecutive paragraph lines, joining their inline content.
      *
      * Consecutive lines within the same paragraph are concatenated with an implicit
      * [[Inline.Text]] space between them, mirroring AsciiDoc's line-continuation semantics.
      */
    val paragraph: Parsley[Block] =
        some(paragraphLine)
            .map(lines => Block.Paragraph(lines.flatten))
            .label("paragraph")
