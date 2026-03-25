package io.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.{atomic, lookAhead, notFollowedBy, pure}
import parsley.character.{char, satisfy, string, stringOfSome}
import parsley.combinator.{many, manyTill, option, sepEndBy, some}
import parsley.errors.combinator.ErrorMethods
import parsley.position.pos

import io.eleven19.ascribe.ast.{
    AttributeList,
    Block,
    BlockTitle,
    CellContent,
    CellSpecifier,
    CommentBlock,
    ExampleBlock,
    Heading,
    InlineContent,
    ListItem,
    ListingBlock,
    LiteralBlock,
    OpenBlock,
    OrderedList,
    Paragraph,
    PassBlock,
    QuoteBlock,
    SidebarBlock,
    Span as AstSpan,
    TableBlock,
    TableCell,
    TableFormat,
    TableRow,
    Text,
    UnorderedList,
    mkSpan
}
import io.eleven19.ascribe.ast.AttributeList.{AttributeName, AttributeValue, OptionName, RoleName}
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

    /** One or more consecutive blank lines. */
    private val blankLine: Parsley[Unit] = (hspaces *> eol).void

    // -----------------------------------------------------------------------
    // Generic delimited block parsers (variable-length fences)
    // -----------------------------------------------------------------------

    /** Parses a delimiter line: 'minLen' or more repetitions of 'delimChar', alone on a line. Returns the matched
      * delimiter string.
      */
    private def delimiterLine(delimChar: Char, minLen: Int): Parsley[String] =
        some(char(delimChar)).map(_.mkString).filter(_.length >= minLen) <* eolOrEof

    /** Parses a verbatim delimited block (listing, literal, comment, passthrough). Content lines are collected as raw
      * strings; no inline parsing. The closing delimiter must match the opening delimiter exactly.
      */
    private def verbatimDelimitedBlock[B <: Block](
        delimChar: Char,
        minLen: Int,
        build: (String, String, Option[AttributeList], Option[BlockTitle], AstSpan) => B
    ): Parsley[Block] =
        val withAttrs = atomic(
            pos <~> option(atomic(blockTitle)) <~> attributeLists <~>
                delimiterLine(delimChar, minLen)
        ).flatMap { case (((s, title), attrs), delim) =>
            (manyTill(rawLine, atomic(string(delim))) <~> pos <* eolOrEof)
                .map { case (lines, e) =>
                    val content = lines.mkString("\n").stripSuffix("\n")
                    build(delim, content, attrs, title, mkSpan(s, e))
                }
        }
        val plain = atomic(
            pos <~> delimiterLine(delimChar, minLen)
        ).flatMap { case (s, delim) =>
            (manyTill(rawLine, atomic(string(delim))) <~> pos <* eolOrEof)
                .map { case (lines, e) =>
                    val content = lines.mkString("\n").stripSuffix("\n")
                    build(delim, content, None, None, mkSpan(s, e))
                }
        }
        withAttrs | plain

    /** Parses a compound delimited block (sidebar, example, quote, open). Content is recursively parsed as blocks. The
      * closing delimiter must match the opening delimiter exactly.
      */
    private def compoundDelimitedBlock[B <: Block](
        delimChar: Char,
        minLen: Int,
        build: (String, List[Block], Option[AttributeList], Option[BlockTitle], AstSpan) => B
    ): Parsley[Block] =
        val withAttrs = atomic(
            pos <~> option(atomic(blockTitle)) <~> attributeLists <~>
                delimiterLine(delimChar, minLen)
        ).flatMap { case (((s, title), attrs), delim) =>
            (manyTill(
                compoundInnerBlock(delim),
                atomic(string(delim))
            ) <~> pos <* eolOrEof)
                .map { case (blocks, e) =>
                    build(delim, blocks, attrs, title, mkSpan(s, e))
                }
        }
        val plain = atomic(
            pos <~> delimiterLine(delimChar, minLen)
        ).flatMap { case (s, delim) =>
            (manyTill(
                compoundInnerBlock(delim),
                atomic(string(delim))
            ) <~> pos <* eolOrEof)
                .map { case (blocks, e) =>
                    build(delim, blocks, None, None, mkSpan(s, e))
                }
        }
        withAttrs | plain

    /** Blocks allowed inside a compound delimited block. Includes all delimited blocks (for nesting) plus headings,
      * lists, and paragraphs.
      */
    /** Blocks allowed inside a compound delimited block. Open blocks cannot nest inside open blocks per spec. */
    private def compoundInnerBlock(parentDelim: String): Parsley[Block] =
        val delimitedBlocks =
            if parentDelim == "--" then
                // Open blocks cannot nest inside open blocks per AsciiDoc spec
                listingBlock | literalBlock | commentBlock | passBlock |
                    sidebarBlock | exampleBlock | quoteBlock
            else
                listingBlock | literalBlock | commentBlock | passBlock |
                    sidebarBlock | exampleBlock | quoteBlock | openBlock
        option(some(blankLine)).void *>
            (delimitedBlocks | heading | unorderedList | orderedList | paragraph)

    // -----------------------------------------------------------------------
    // Concrete delimited block parsers
    // -----------------------------------------------------------------------

    /** Listing block: `----` (4+ dashes), verbatim content. */
    val listingBlock: Parsley[Block] =
        verbatimDelimitedBlock(
            '-',
            4,
            (delim, content, attrs, title, span) => ListingBlock(delim, content, attrs, title)(span)
        ).label("listing block")

    /** Literal block: `....` (4+ dots), verbatim content. */
    val literalBlock: Parsley[Block] =
        verbatimDelimitedBlock(
            '.',
            4,
            (delim, content, attrs, title, span) => LiteralBlock(delim, content, attrs, title)(span)
        ).label("literal block")

    /** Comment block: `////` (4+ slashes), content is discarded. */
    val commentBlock: Parsley[Block] =
        verbatimDelimitedBlock(
            '/',
            4,
            (delim, content, _, _, span) => CommentBlock(delim, content)(span)
        ).label("comment block")

    /** Passthrough block: `++++` (4+ pluses), raw content. */
    val passBlock: Parsley[Block] =
        verbatimDelimitedBlock(
            '+',
            4,
            (delim, content, attrs, title, span) => PassBlock(delim, content, attrs, title)(span)
        ).label("passthrough block")

    /** Sidebar block: `****` (4+ asterisks), compound content. */
    val sidebarBlock: Parsley[Block] =
        compoundDelimitedBlock(
            '*',
            4,
            (delim, blocks, attrs, title, span) => SidebarBlock(delim, blocks, attrs, title)(span)
        ).label("sidebar block")

    /** Example block: `====` (4+ equals signs), compound content. Can be repurposed as admonition via
      * `[NOTE]`/`[TIP]`/etc. style.
      */
    val exampleBlock: Parsley[Block] =
        compoundDelimitedBlock(
            '=',
            4,
            (delim, blocks, attrs, title, span) => ExampleBlock(delim, blocks, attrs, title)(span)
        ).label("example block")

    /** Quote block: `____` (4+ underscores), compound content. Can be repurposed as verse via `[verse]` style.
      */
    val quoteBlock: Parsley[Block] =
        compoundDelimitedBlock(
            '_',
            4,
            (delim, blocks, attrs, title, span) => QuoteBlock(delim, blocks, attrs, title)(span)
        ).label("quote block")

    /** Open block: `--` (exactly 2 dashes, not 3+), compound content. Fixed length per spec. Cannot nest inside another
      * open block.
      */
    val openBlock: Parsley[Block] =
        val openDelim = string("--") <* notFollowedBy(char('-'))
        val withAttrs = atomic(
            pos <~> option(atomic(blockTitle)) <~> attributeLists <~>
                (openDelim <* eolOrEof)
        ).flatMap { case (((s, title), attrs), _) =>
            (manyTill(
                compoundInnerBlock("--"),
                atomic(openDelim)
            ) <~> pos <* eolOrEof)
                .map { case (blocks, e) =>
                    OpenBlock("--", blocks, attrs, title)(mkSpan(s, e))
                }
        }
        val plain = atomic(
            pos <~> (openDelim <* eolOrEof)
        ).flatMap { case (s, _) =>
            (manyTill(
                compoundInnerBlock("--"),
                atomic(openDelim)
            ) <~> pos <* eolOrEof)
                .map { case (blocks, e) =>
                    OpenBlock("--", blocks, None, None)(mkSpan(s, e))
                }
        }
        (withAttrs | plain).label("open block")

    // -----------------------------------------------------------------------
    // Attribute lists
    // -----------------------------------------------------------------------

    /** ADT for individual entries within an attribute list. */
    private enum AttrEntry:
        case Named(key: AttributeName, value: AttributeValue)
        case Opt(name: OptionName)
        case Role(name: RoleName)
        case Positional(value: AttributeValue)

    /** Parses a double-quoted string value (content between `"` delimiters). */
    private val quotedValue: Parsley[String] =
        char('"') *> stringOfSome(satisfy(c => c != '"' && c != '\n' && c != '\r')) <* char('"')

    /** Parses an unquoted attribute value (stops at `,`, `]`, `"`, newline, and shorthand prefixes). */
    private val unquotedValue: Parsley[String] =
        stringOfSome(
            satisfy(c =>
                c != ',' && c != ']' && c != '"' && c != '=' && c != '\n' && c != '\r' && c != '%' && c != '#' && c != '.'
            )
        )

    /** Characters that delimit shorthand entries in an attribute list. */
    private val shorthandChars = Set(',', ']', '%', '#', '.', '\n', '\r')

    /** Parses a single attribute entry: option (`%name`), id shorthand (`#id`), role shorthand (`.role`), named
      * (`key=value`), or positional (bare value).
      */
    private val attrEntry: Parsley[AttrEntry] =
        (char('%') *> stringOfSome(satisfy(c => !shorthandChars(c))))
            .map(s => AttrEntry.Opt(OptionName(s.trim))) |
            (char('#') *> stringOfSome(satisfy(c => !shorthandChars(c))))
                .map(s => AttrEntry.Named(AttributeName("id"), AttributeValue(s.trim))) |
            (char('.') *> notFollowedBy(char('.')) *> stringOfSome(satisfy(c => !shorthandChars(c))))
                .map(s => AttrEntry.Role(RoleName(s.trim))) |
            atomic((unquotedValue <* char('=')) <~> (quotedValue | unquotedValue))
                .map { case (k, v) => AttrEntry.Named(AttributeName(k.trim), AttributeValue(v.trim)) } |
            (quotedValue | unquotedValue)
                .map(s => AttrEntry.Positional(AttributeValue(s.trim)))

    /** Parses a single attribute list line: `[key=value, %option, .role]`. */
    val attributeListLine: Parsley[AttributeList] =
        (pos <~> (char('[') *> sepEndBy(attrEntry, option(char(',') *> option(char(' ')).void).void) <* char(
            ']'
        ) <* eolOrEof) <~> pos)
            .map { case ((s, entries), e) =>
                val positional = entries.collect { case AttrEntry.Positional(v) => v }.toList
                val named      = entries.collect { case AttrEntry.Named(k, v) => (k, v) }.toMap
                val options    = entries.collect { case AttrEntry.Opt(o) => o }.toList
                val roles      = entries.collect { case AttrEntry.Role(r) => r }.toList
                AttributeList(positional, named, options, roles)(mkSpan(s, e))
            }
            .label("attribute list")

    /** Parses zero or more attribute list lines and merges them into a single optional [[AttributeList]]. */
    val attributeLists: Parsley[Option[AttributeList]] =
        many(attributeListLine).map {
            case Nil => None
            case first :: rest =>
                Some(
                    rest.foldLeft(first)((acc, al) =>
                        AttributeList.merge(acc, al)(io.eleven19.ascribe.ast.Span(acc.span.start, al.span.end))
                    )
                )
        }

    // -----------------------------------------------------------------------
    // Block title
    // -----------------------------------------------------------------------

    /** Parses a block title line: `.TitleText` (dot followed by non-dot, non-space content). */
    val blockTitle: Parsley[BlockTitle] =
        (pos <~> (char('.') *> notFollowedBy(char('.')) *> notFollowedBy(char(' ')) *> lineContent) <~> pos <* eolOrEof)
            .map { case ((s, content), e) => BlockTitle(content)(mkSpan(s, e)) }
            .label("block title")

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

    import CellSpecifier.{ColSpanFactor, DupFactor, RowSpanFactor, StyleOperator}

    /** Parsed cell specifier: optional span/duplication factors and style operator. */
    private case class CellSpec(
        colSpan: Option[ColSpanFactor] = None,
        rowSpan: Option[RowSpanFactor] = None,
        dupFactor: Option[DupFactor] = None,
        style: Option[StyleOperator] = None
    )

    /** Parses a cell specifier prefix before `|`.
      *   - Span: `2+|`, `.3+|`, `2.3+|`, `2+s|`
      *   - Duplication: `3*|`, `3*s|`
      *   - Style only: `s|`
      */
    private val cellSpecifier: Parsley[CellSpec] = atomic {
        import parsley.character.digit
        val digits  = some(digit).map(_.mkString.toInt)
        val styleOp = option(satisfy(c => "adehilms".contains(c)).map(StyleOperator(_)))

        // Span: [col][.row]+[style]|
        val spanSpec = atomic {
            val colFactor = option(digits.map(ColSpanFactor(_)))
            val rowFactor = option(char('.') *> digits.map(RowSpanFactor(_)))
            (colFactor <~> rowFactor <* char('+') <~> styleOp <* char('|')).map { case ((col, row), sty) =>
                CellSpec(colSpan = col, rowSpan = row, style = sty)
            }
        }

        // Duplication: <n>*[style]|
        val dupSpec = atomic(
            (digits.map(DupFactor(_)) <* char('*') <~> styleOp <* char('|')).map { case (dup, sty) =>
                CellSpec(dupFactor = Some(dup), style = sty)
            }
        )

        // Style only: <style>|
        val styleSpec = atomic((styleOp.filter(_.isDefined) <* char('|')).map(sty => CellSpec(style = sty)))

        spanSpec | dupSpec | styleSpec
    }

    /** Parses a single cell with optional specifier: `[spec]| content` or plain `| content`. */
    private val tableCell: Parsley[TableCell] =
        val specifiedCell =
            (pos <~> cellSpecifier <~> option(char(' ')).void <~> cellContent <~> pos)
                .map { case ((((s, spec), _), content), e) =>
                    TableCell(
                        CellContent.Inlines(trimCellContent(content)),
                        spec.style,
                        spec.colSpan,
                        spec.rowSpan,
                        spec.dupFactor
                    )(
                        mkSpan(s, e)
                    )
                }
        val plainCell =
            (pos <~> (char('|') *> option(char(' ')).void *> cellContent) <~> pos)
                .map { case ((s, content), e) =>
                    TableCell(CellContent.Inlines(trimCellContent(content)))(mkSpan(s, e))
                }
        specifiedCell | plainCell

    /** Parses a row of cells on a single line ending with eolOrEof. */
    private val tableRowLine: Parsley[List[TableCell]] =
        some(tableCell).map(_.toList) <* eolOrEof

    /** Parses a complete table row (possibly spanning multiple lines separated by blank lines). */
    private val tableRow: Parsley[TableRow] =
        (pos <~> tableRowLine <~> pos).map { case ((s, cells), e) => TableRow(cells)(mkSpan(s, e)) }

    /** Parses table rows while tracking whether a blank line appears after the first row.
      *
      * This information is needed for implicit header row detection in AsciiDoc tables.
      */
    private val tableRowsWithBlankTracking: Parsley[(List[TableRow], Boolean)] =
        option(tableRow).flatMap {
            case None =>
                atomic(string("|===")).map(_ => (Nil, false))
            case Some(firstRow) =>
                val blankThenMore = some(blankLine).void *>
                    manyTill(
                        tableRow <* option(some(blankLine)).void,
                        atomic(string("|==="))
                    ).map(rest => (firstRow :: rest, true))
                val noBlankMore = manyTill(
                    tableRow <* option(some(blankLine)).void,
                    atomic(string("|==="))
                ).map(rest => (firstRow :: rest, false))
                blankThenMore | noBlankMore |
                    atomic(string("|===")).map(_ => (List(firstRow), false))
        }

    // --- CSV/DSV data format parsers ---

    /** Parses a CSV row: comma-separated values with optional double-quote enclosing. */
    private def csvRow(sep: Char): Parsley[List[TableCell]] =
        val unquoted = stringOfSome(satisfy(c => c != sep && c != '"' && c != '\n' && c != '\r'))
        val quoted   = char('"') *> many(satisfy(_ != '"') | (char('"') *> char('"'))).map(_.mkString) <* char('"')
        val cellVal  = option(quoted | unquoted).map(_.getOrElse("").trim)
        val cell = (pos <~> cellVal <~> pos).map { case ((s, v), e) =>
            val content: InlineContent =
                if v.isEmpty then Nil
                else scala.List(io.eleven19.ascribe.ast.Text(v)(mkSpan(s, e)))
            TableCell(CellContent.Inlines(content))(mkSpan(s, e))
        }
        (cell <~> many(char(sep) *> cell)).map { case (first, rest) => first :: rest } <* eolOrEof

    /** Parses a DSV row: separator-separated values with backslash escaping. */
    private def dsvRow(sep: Char): Parsley[List[TableCell]] =
        val escaped = char('\\') *> satisfy(_ => true)
        val cellVal = many(escaped | satisfy(c => c != sep && c != '\n' && c != '\r')).map(_.mkString.trim)
        val cell = (pos <~> cellVal <~> pos).map { case ((s, v), e) =>
            val content: InlineContent =
                if v.isEmpty then Nil
                else scala.List(io.eleven19.ascribe.ast.Text(v)(mkSpan(s, e)))
            TableCell(CellContent.Inlines(content))(mkSpan(s, e))
        }
        (cell <~> many(char(sep) *> cell)).map { case (first, rest) => first :: rest } <* eolOrEof

    /** Parses data-format table rows (CSV/DSV/TSV) until closing delimiter. */
    private def dataTableRows(sep: Char, useCsv: Boolean, closingDelim: String): Parsley[List[TableRow]] =
        val rowParser = if useCsv then csvRow(sep) else dsvRow(sep)
        val row       = (pos <~> rowParser <~> pos).map { case ((s, cells), e) => TableRow(cells)(mkSpan(s, e)) }
        manyTill(
            (row <* option(some(blankLine)).void) | (blankLine.map(_ => null)),
            atomic(string(closingDelim))
        ).map(_.filter(_ != null))

    // --- Nested table parser (uses ! separator and !=== delimiter) ---

    /** Cell content for nested tables: text that isn't `!` or newline. */
    private val nestedCellContent: Parsley[InlineContent] =
        many(
            boldSpan | constrainedBoldSpan | italicSpan | monoSpan |
                Text(stringOfSome(satisfy(c => isContentChar(c) && c != '!'))) |
                (pos <~> unpairedMarkupChar <~> pos).map { case ((s, c), e) =>
                    io.eleven19.ascribe.ast.Text(c.toString)(mkSpan(s, e)): io.eleven19.ascribe.ast.Inline
                }
        )

    private val nestedTableCell: Parsley[TableCell] =
        (pos <~> (char('!') *> option(char(' ')).void *> nestedCellContent) <~> pos)
            .map { case ((s, content), e) =>
                TableCell(CellContent.Inlines(trimCellContent(content)))(mkSpan(s, e))
            }

    private val nestedTableRowLine: Parsley[List[TableCell]] =
        some(nestedTableCell).map(_.toList) <* eolOrEof

    private val nestedTableRow: Parsley[TableRow] =
        (pos <~> nestedTableRowLine <~> pos).map { case ((s, cells), e) => TableRow(cells)(mkSpan(s, e)) }

    private val nestedTableRows: Parsley[List[TableRow]] =
        manyTill(
            nestedTableRow <* option(some(blankLine)).void,
            atomic(string("!==="))
        )

    /** Parses a nested table block: `!===` delimiter with `!` cell separator. */
    val nestedTableBlock: Parsley[Block] =
        (pos <~> option(atomic(blockTitle)) <~> attributeLists <~>
            (atomic(string("!===")) <* eolOrEof) *>
            option(some(blankLine)).void *>
            nestedTableRows <~> pos <* eolOrEof)
            .map { case ((((s, title), attrs), rows), e) =>
                TableBlock(rows, "!===", TableFormat.PSV, attrs, title)(mkSpan(s, e))
            }
            .label("nested table block")

    // --- Multi-line a-style cell block content ---

    /** Parses block content within an a-style cell. Stops when it sees `|` at start of line or `|===`. */
    private val aCellBlock: Parsley[Block] =
        nestedTableBlock | listingBlock | literalBlock | commentBlock | passBlock |
            sidebarBlock | exampleBlock | quoteBlock | openBlock |
            unorderedList | orderedList |
            // Paragraph: lines that don't start with |, !, delimiters
            (pos <~> some(
                notFollowedBy(char('|')) *> notFollowedBy(string("!===")) *>
                    notBlockStart *>
                    some(inlineElement) <* eolOrEof
            ).map(_.toList.flatten) <~> pos)
                .map { case ((s, content), e) => Paragraph(content)(mkSpan(s, e)) }

    /** Parses multi-line block content for an a-style cell, stopping at next outer `|` or `|===`. Uses lookAhead so the
      * terminator is NOT consumed (the next cell or |=== will consume it).
      */
    val aCellContent: Parsley[List[Block]] =
        manyTill(
            aCellBlock <* option(some(blankLine)).void,
            lookAhead(atomic(string("|==="))) | lookAhead(atomic(char('|')))
        )

    /** Parses a multi-line a-style cell: `|` followed by block content across multiple lines. Content continues until
      * the next `|` at start of line or `|===`. Does not match `|===` (that's the table closing delimiter).
      */
    private val multiLineCell: Parsley[TableCell] =
        (pos <~> (notFollowedBy(string("|===")) *> char('|') *> option(char(' ')).void) *> aCellContent <~> pos)
            .map { case ((s, blocks), e) =>
                TableCell(CellContent.Blocks(blocks))(mkSpan(s, e))
            }

    /** Parses a multi-line table row where cells start with `|` at line beginnings. Each cell may span multiple lines
      * (for a-style block content). Stops at `|===`.
      */
    private val multiLineTableRow: Parsley[TableRow] =
        (pos <~> some(multiLineCell).map(_.toList) <~> pos)
            .map { case ((s, cells), e) => TableRow(cells)(mkSpan(s, e)) }

    /** Parses rows in multi-line mode (for tables with a-style columns). */
    private val multiLineTableRows: Parsley[List[TableRow]] =
        manyTill(
            multiLineTableRow <* option(some(blankLine)).void,
            atomic(string("|==="))
        )

    /** Check if an attribute list contains an a-style column. */
    private def hasAStyleColumn(attrs: Option[AttributeList]): Boolean =
        attrs
            .map(_.named.map((k, v) => (k.value, v.value)))
            .getOrElse(Map.empty)
            .get("cols")
            .exists(_.contains('a'))

    /** Parses a table block: PSV (`|===`), CSV (`,===`), or DSV (`:===`). */
    val tableBlock: Parsley[Block] =
        val psvTable =
            (pos <~> option(atomic(blockTitle)) <~> attributeLists <~>
                (atomic(string("|===")) <* eolOrEof))
                .flatMap { case (((s, title), attrs), _) =>
                    option(some(blankLine)).void *> (
                        if hasAStyleColumn(attrs) then
                            // Multi-line mode for tables with a-style columns
                            (option(tableRowLine <* some(blankLine)).map(
                                _.map(cells => TableRow(cells)(mkSpan(s, s)))
                            ) <~> multiLineTableRows <~> pos <* eolOrEof)
                                .map { case ((headerRow, rows), e) =>
                                    val allRows            = headerRow.toList ++ rows
                                    val hasBlankAfterFirst = headerRow.isDefined
                                    TableBlock(allRows, "|===", TableFormat.PSV, attrs, title, hasBlankAfterFirst)(
                                        mkSpan(s, e)
                                    )
                                }
                        else
                            // Single-line mode (existing behavior)
                            (tableRowsWithBlankTracking <~> pos <* eolOrEof)
                                .map { case ((rows, hasBlankAfterFirst), e) =>
                                    val format = attrs
                                        .map(_.named.map((k, v) => (k.value, v.value)))
                                        .getOrElse(Map.empty)
                                        .get("format") match
                                        case Some("csv") => TableFormat.CSV
                                        case Some("tsv") => TableFormat.TSV
                                        case Some("dsv") => TableFormat.DSV
                                        case _           => TableFormat.PSV
                                    TableBlock(rows, "|===", format, attrs, title, hasBlankAfterFirst)(mkSpan(s, e))
                                }
                    )
                }

        val csvTable =
            (pos <~> option(atomic(blockTitle)) <~> attributeLists <~>
                (atomic(string(",===")) <* eolOrEof) *>
                option(some(blankLine)).void *>
                dataTableRows(',', true, ",===") <~> pos <* eolOrEof)
                .map { case ((((s, title), attrs), rows), e) =>
                    TableBlock(rows, ",===", TableFormat.CSV, attrs, title)(mkSpan(s, e))
                }

        val dsvTable =
            (pos <~> option(atomic(blockTitle)) <~> attributeLists <~>
                (atomic(string(":===")) <* eolOrEof) *>
                option(some(blankLine)).void *>
                dataTableRows(':', false, ":===") <~> pos <* eolOrEof)
                .map { case ((((s, title), attrs), rows), e) =>
                    TableBlock(rows, ":===", TableFormat.DSV, attrs, title)(mkSpan(s, e))
                }

        (psvTable | csvTable | dsvTable).label("table block")

    // -----------------------------------------------------------------------
    // Paragraphs
    // -----------------------------------------------------------------------

    /** Negative lookahead for any block-starting prefix.
      *
      * Prevents [[paragraphLine]] from consuming lines that belong to a heading, list, block title, attribute list, or
      * delimited block.
      */
    private val notBlockStart: Parsley[Unit] =
        notFollowedBy(headingLevel *> char(' ')) *>
            notFollowedBy(char('*') *> char(' ')) *>
            notFollowedBy(char('.') *> char(' ')) *>
            notFollowedBy(char('.') *> satisfy(c => c != '.' && c != ' ' && c != '\n' && c != '\r')) *>
            notFollowedBy(char('[') *> satisfy(c => c != '\n' && c != '\r')) *>
            notFollowedBy(string("----")) *>                           // listing
            notFollowedBy(string("....")) *>                           // literal
            notFollowedBy(string("****")) *>                           // sidebar
            notFollowedBy(string("====")) *>                           // example
            notFollowedBy(string("____")) *>                           // quote
            notFollowedBy(string("////")) *>                           // comment
            notFollowedBy(string("++++")) *>                           // passthrough
            notFollowedBy(string("--") <* notFollowedBy(char('-'))) *> // open block (exactly --)
            notFollowedBy(string("|===")) *>
            notFollowedBy(string("!===")) *>
            notFollowedBy(string(",===")) *>
            notFollowedBy(string(":==="))

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
