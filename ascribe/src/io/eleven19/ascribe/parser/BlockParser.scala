package io.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.{atomic, notFollowedBy}
import parsley.character.{char, satisfy, string, stringOfSome}
import parsley.combinator.{many, manyTill, option, some}
import parsley.errors.combinator.ErrorMethods
import parsley.position.pos

import io.eleven19.ascribe.ast.{
    Block,
    Heading,
    InlineContent,
    ListItem,
    ListingBlock,
    OrderedList,
    Paragraph,
    SidebarBlock,
    Span as AstSpan,
    TableBlock,
    TableCell,
    TableRow,
    Text,
    UnorderedList,
    mkSpan
}
import io.eleven19.ascribe.lexer.AsciiDocLexer.*
import io.eleven19.ascribe.parser.InlineParser.*

/** Parsers for block-level AsciiDoc elements.
  *
  * Each parser recognises exactly one kind of block and is meant to be composed by [[DocumentParser]]. Parsers that
  * start with a distinctive prefix (heading markers, list markers) are wrapped in [[atomic]] so that failures produce
  * clean backtracking.
  *
  * ==Supported blocks==
  *   - `= Title` through `===== Title` -- [[heading]]
  *   - `* item` -- [[unorderedList]]
  *   - `. item` -- [[orderedList]]
  *   - everything else -- [[paragraph]]
  */
object BlockParser:

    // -----------------------------------------------------------------------
    // Headings
    // -----------------------------------------------------------------------

    /** Parses the leading `=`-markers of a heading and returns the heading level (1-5).
      *
      * Tries longer sequences first to avoid the two-character `==` matching before `===`.
      */
    private val headingLevel: Parsley[Int] =
        atomic(string("=====")).as(5) |
            atomic(string("====")).as(4) |
            atomic(string("===")).as(3) |
            atomic(string("==")).as(2) |
            atomic(string("=")).as(1)

    /** Parses a section heading using the Heading bridge constructor.
      *
      * Syntax: one to five `=` characters, a single space, then the title on the rest of the line. Level is determined
      * by the number of `=` signs.
      *
      * {{{
      * = Document title
      * == Chapter
      * === Section
      * }}}
      */
    val heading: Parsley[Block] =
        atomic(Heading(headingLevel <* char(' '), lineContent <* eolOrEof))
            .label("heading")
            .explain(
                "A heading starts with one to five equals signs followed by a space, e.g. = Title"
            )

    // -----------------------------------------------------------------------
    // Lists
    // -----------------------------------------------------------------------

    /** Parses a single unordered list item line: `* content`. */
    private val unorderedItem: Parsley[ListItem] =
        atomic(ListItem(char('*') *> char(' ') *> lineContent <* eolOrEof))
            .label("unordered list item")

    /** Parses one or more consecutive `* item` lines as an [[UnorderedList]]. */
    val unorderedList: Parsley[Block] =
        UnorderedList(some(unorderedItem).map(_.toList))
            .label("unordered list")

    /** Parses a single ordered list item line: `. content`. */
    private val orderedItem: Parsley[ListItem] =
        atomic(ListItem(char('.') *> char(' ') *> lineContent <* eolOrEof))
            .label("ordered list item")

    /** Parses one or more consecutive `. item` lines as an [[OrderedList]]. */
    val orderedList: Parsley[Block] =
        OrderedList(some(orderedItem).map(_.toList))
            .label("ordered list")

    // -----------------------------------------------------------------------
    // Delimited blocks
    // -----------------------------------------------------------------------

    /** Parses a line of text (any chars until newline), returning the content without the newline. */
    private val rawLine: Parsley[String] =
        many(nonEolChar).map(_.mkString) <* eolOrEof

    /** Parses a delimited listing block: `----` open, verbatim content, `----` close. */
    val listingBlock: Parsley[Block] =
        (pos <~> (atomic(string("----")) <* eolOrEof) *>
            manyTill(rawLine, atomic(string("----"))) <~> pos <* eolOrEof)
            .map { case ((s, lines), e) =>
                val content = lines.mkString("\n").stripSuffix("\n")
                ListingBlock("----", content)(mkSpan(s, e))
            }
            .label("listing block")

    /** Parses a delimited sidebar block: `****` open, nested blocks, `****` close. */
    val sidebarBlock: Parsley[Block] =
        (pos <~> (atomic(string("****")) <* eolOrEof) *>
            manyTill(
                sidebarInnerBlock,
                atomic(string("****"))
            ) <~> pos <* eolOrEof)
            .map { case ((s, blocks), e) =>
                SidebarBlock("****", blocks)(mkSpan(s, e))
            }
            .label("sidebar block")

    /** Blocks allowed inside a sidebar (same as top-level blocks except delimited blocks). */
    private lazy val sidebarInnerBlock: Parsley[Block] =
        option(some(blankLine)).void *> (heading | unorderedList | orderedList | paragraph)

    /** One or more consecutive blank lines. */
    private val blankLine: Parsley[Unit] = (hspaces *> eol).void

    // -----------------------------------------------------------------------
    // Tables
    // -----------------------------------------------------------------------

    /** Content of a single table cell (everything after `| ` until next `|` or end of line). Stops consuming before
      * trailing spaces that precede a `|` or end of line.
      */
    private val cellContent: Parsley[InlineContent] =
        many(
            boldSpan | constrainedBoldSpan | italicSpan | monoSpan |
                Text(stringOfSome(satisfy(c => isContentChar(c) && c != '|'))) |
                (pos <~> unpairedMarkupChar <~> pos).map { case ((s, c), e) =>
                    io.eleven19.ascribe.ast.Text(c.toString)(mkSpan(s, e)): io.eleven19.ascribe.ast.Inline
                }
        )

    /** Trim trailing whitespace from the last Text node in a cell's inline content. */
    private def trimCellContent(content: InlineContent): InlineContent =
        content match
            case init :+ (t: io.eleven19.ascribe.ast.Text) =>
                val trimmed = t.content.stripTrailing()
                if trimmed.isEmpty then init
                else init :+ io.eleven19.ascribe.ast.Text(trimmed)(t.span)
            case other => other

    /** Parses a single cell: `| content`. Trims trailing whitespace from cell text. */
    private val tableCell: Parsley[TableCell] =
        (pos <~> (char('|') *> option(char(' ')).void *> cellContent) <~> pos)
            .map { case ((s, content), e) => TableCell(trimCellContent(content))(mkSpan(s, e)) }

    /** Parses a row of cells on a single line ending with eolOrEof. */
    private val tableRowLine: Parsley[List[TableCell]] =
        some(tableCell).map(_.toList) <* eolOrEof

    /** Parses a complete table row (possibly spanning multiple lines separated by blank lines). */
    private val tableRow: Parsley[TableRow] =
        (pos <~> tableRowLine <~> pos).map { case ((s, cells), e) => TableRow(cells)(mkSpan(s, e)) }

    /** Parses a table block: `|===` open, rows, `|===` close. */
    val tableBlock: Parsley[Block] =
        (pos <~> (atomic(string("|===")) <* eolOrEof) *>
            option(some(blankLine)).void *>
            manyTill(
                tableRow <* option(some(blankLine)).void,
                atomic(string("|==="))
            ) <~> pos <* eolOrEof)
            .map { case ((s, rows), e) =>
                TableBlock(rows, "|===")(mkSpan(s, e))
            }
            .label("table block")

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
            notFollowedBy(char('.') *> char(' ')) *>
            notFollowedBy(string("----")) *>
            notFollowedBy(string("****")) *>
            notFollowedBy(string("|==="))

    /** Parses a single non-empty, non-block-start line as a list of inline elements. */
    private val paragraphLine: Parsley[InlineContent] =
        (notBlockStart *> some(inlineElement) <* eolOrEof).label("paragraph line")

    /** Parses one or more consecutive paragraph lines, joining their inline content.
      *
      * Consecutive lines within the same paragraph are concatenated, mirroring AsciiDoc's line-continuation semantics.
      */
    val paragraph: Parsley[Block] =
        Paragraph(some(paragraphLine).map(_.flatten))
            .label("paragraph")
