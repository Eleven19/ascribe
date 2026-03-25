package io.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.{atomic, lookAhead, many, notFollowedBy, some}
import parsley.character.{char, satisfy, string, stringOfSome}
import parsley.combinator.{manyTill, option, sepEndBy}
import parsley.errors.combinator.ErrorMethods
import parsley.position.pos

import io.eleven19.ascribe.ast.{Span as AstSpan, TableFormat, mkSpan}
import io.eleven19.ascribe.ast.AttributeList.{AttributeName, AttributeValue, OptionName, RoleName}
import io.eleven19.ascribe.cst.*
import io.eleven19.ascribe.lexer.AsciiDocLexer.*
import io.eleven19.ascribe.parser.InlineParser.*

/** Parsers for block-level AsciiDoc elements.
  *
  * Each parser recognises exactly one kind of block and is meant to be composed by [[DocumentParser]]. Parsers that
  * start with a distinctive prefix (heading markers, list markers) are wrapped in [[atomic]] so that failures produce
  * clean backtracking.
  *
  * All parsers emit CST nodes (`CstBlock`, `CstTopLevel`, etc.).
  */
object BlockParser:

    // -----------------------------------------------------------------------
    // Headings
    // -----------------------------------------------------------------------

    private val headingLevel: Parsley[Int] =
        atomic(string("=====")).as(5) |
            atomic(string("====")).as(4) |
            atomic(string("===")).as(3) |
            atomic(string("==")).as(2) |
            atomic(string("=")).as(1)

    val heading: Parsley[CstBlock] =
        atomic(
            (pos <~> (headingLevel <~> char(' ') *> lineContent) <~> pos <* eolOrEof)
                .map { case ((s, (level, title)), e) =>
                    val marker = "=" * level
                    CstHeading(level, marker, title)(mkSpan(s, e))
                }
        ).label("heading")
            .explain(
                "A heading starts with one to five equals signs followed by a space, e.g. = Title"
            )

    // -----------------------------------------------------------------------
    // Lists
    // -----------------------------------------------------------------------

    private val unorderedItem: Parsley[CstListItem] =
        atomic(
            (pos <~> (char('*') *> char(' ') *> lineContent) <~> pos <* eolOrEof)
                .map { case ((s, content), e) => CstListItem("* ", content)(mkSpan(s, e)) }
        ).label("unordered list item")

    val unorderedList: Parsley[CstBlock] =
        (pos <~> some(unorderedItem).map(_.toList) <~> pos)
            .map { case ((s, items), e) => CstList(ListVariant.Unordered, items)(mkSpan(s, e)) }
            .label("unordered list")

    private val orderedItem: Parsley[CstListItem] =
        atomic(
            (pos <~> (char('.') *> char(' ') *> lineContent) <~> pos <* eolOrEof)
                .map { case ((s, content), e) => CstListItem(". ", content)(mkSpan(s, e)) }
        ).label("ordered list item")

    val orderedList: Parsley[CstBlock] =
        (pos <~> some(orderedItem).map(_.toList) <~> pos)
            .map { case ((s, items), e) => CstList(ListVariant.Ordered, items)(mkSpan(s, e)) }
            .label("ordered list")

    // -----------------------------------------------------------------------
    // Delimited blocks
    // -----------------------------------------------------------------------

    private val rawLine: Parsley[String] =
        many(nonEolChar).map(_.mkString) <* eolOrEof

    /** One or more consecutive blank lines (consumed as separator, not captured). */
    private val blankLine: Parsley[Unit] = (hspaces *> eol).void

    // -----------------------------------------------------------------------
    // Generic delimited block parsers (variable-length fences)
    // -----------------------------------------------------------------------

    private def delimiterLine(delimChar: Char, minLen: Int): Parsley[String] =
        some(char(delimChar)).map(_.mkString).filter(_.length >= minLen) <* eolOrEof

    private def verbatimDelimitedBlock(
        delimChar: Char,
        minLen: Int,
        kind: DelimitedBlockKind
    ): Parsley[CstBlock] =
        val withAttrs = atomic(
            pos <~> option(atomic(blockTitle)) <~> attributeLists <~>
                delimiterLine(delimChar, minLen)
        ).flatMap { case (((s, title), attrs), delim) =>
            (manyTill(rawLine, atomic(string(delim))) <~> pos <* eolOrEof)
                .map { case (lines, e) =>
                    val content = lines.mkString("\n").stripSuffix("\n")
                    CstDelimitedBlock(kind, delim, CstVerbatimContent(content)(mkSpan(s, e)), attrs, title)(
                        mkSpan(s, e)
                    )
                }
        }
        val plain = atomic(pos <~> delimiterLine(delimChar, minLen)).flatMap { case (s, delim) =>
            (manyTill(rawLine, atomic(string(delim))) <~> pos <* eolOrEof)
                .map { case (lines, e) =>
                    val content = lines.mkString("\n").stripSuffix("\n")
                    CstDelimitedBlock(kind, delim, CstVerbatimContent(content)(mkSpan(s, e)), None, None)(mkSpan(s, e))
                }
        }
        withAttrs | plain

    private def compoundDelimitedBlock(
        delimChar: Char,
        minLen: Int,
        kind: DelimitedBlockKind
    ): Parsley[CstBlock] =
        // Stop only on an exact-length delimiter — notFollowedBy(char(delimChar)) prevents matching
        // a shorter delimiter as a prefix of a longer nested delimiter (e.g. ==== inside ======).
        def exactStop(delim: String): Parsley[Unit] =
            atomic((string(delim) <* notFollowedBy(char(delimChar))).void)
        val withAttrs = atomic(
            pos <~> option(atomic(blockTitle)) <~> attributeLists <~>
                delimiterLine(delimChar, minLen)
        ).flatMap { case (((s, title), attrs), delim) =>
            (manyTill(
                compoundInnerBlock(delim),
                exactStop(delim)
            ) <~> pos <* eolOrEof)
                .map { case (children, e) =>
                    CstDelimitedBlock(kind, delim, CstNestedContent(children)(mkSpan(s, e)), attrs, title)(mkSpan(s, e))
                }
        }
        val plain = atomic(pos <~> delimiterLine(delimChar, minLen)).flatMap { case (s, delim) =>
            (manyTill(
                compoundInnerBlock(delim),
                exactStop(delim)
            ) <~> pos <* eolOrEof)
                .map { case (children, e) =>
                    CstDelimitedBlock(kind, delim, CstNestedContent(children)(mkSpan(s, e)), None, None)(mkSpan(s, e))
                }
        }
        withAttrs | plain

    /** Blocks allowed inside a compound delimited block. Includes blank lines, comments, includes, attribute entries,
      * all delimited blocks (for nesting), headings, lists, and paragraphs.
      */
    private def compoundInnerBlock(parentDelim: String): Parsley[CstTopLevel] =
        val delimitedBlocks =
            if parentDelim == "--" then
                // Open blocks cannot nest inside open blocks per AsciiDoc spec
                listingBlock | literalBlock | commentBlock | passBlock |
                    sidebarBlock | exampleBlock | quoteBlock
            else
                listingBlock | literalBlock | commentBlock | passBlock |
                    sidebarBlock | exampleBlock | quoteBlock | openBlock
        cstBlankLine |
            lineCommentBlock | includeDirective | attributeEntryBlock |
            (delimitedBlocks | heading | unorderedList | orderedList | paragraph)

    // -----------------------------------------------------------------------
    // Concrete delimited block parsers
    // -----------------------------------------------------------------------

    val listingBlock: Parsley[CstBlock] =
        verbatimDelimitedBlock('-', 4, DelimitedBlockKind.Listing).label("listing block")

    val literalBlock: Parsley[CstBlock] =
        verbatimDelimitedBlock('.', 4, DelimitedBlockKind.Literal).label("literal block")

    val commentBlock: Parsley[CstBlock] =
        verbatimDelimitedBlock('/', 4, DelimitedBlockKind.Comment).label("comment block")

    val passBlock: Parsley[CstBlock] =
        verbatimDelimitedBlock('+', 4, DelimitedBlockKind.Pass).label("passthrough block")

    val sidebarBlock: Parsley[CstBlock] =
        compoundDelimitedBlock('*', 4, DelimitedBlockKind.Sidebar).label("sidebar block")

    val exampleBlock: Parsley[CstBlock] =
        compoundDelimitedBlock('=', 4, DelimitedBlockKind.Example).label("example block")
    val quoteBlock: Parsley[CstBlock] = compoundDelimitedBlock('_', 4, DelimitedBlockKind.Quote).label("quote block")

    val openBlock: Parsley[CstBlock] =
        val openDelim = string("--") <* notFollowedBy(char('-'))
        val withAttrs = atomic(
            pos <~> option(atomic(blockTitle)) <~> attributeLists <~>
                (openDelim <* eolOrEof)
        ).flatMap { case (((s, title), attrs), _) =>
            (manyTill(
                compoundInnerBlock("--"),
                atomic(openDelim)
            ) <~> pos <* eolOrEof)
                .map { case (children, e) =>
                    CstDelimitedBlock(
                        DelimitedBlockKind.Open,
                        "--",
                        CstNestedContent(children)(mkSpan(s, e)),
                        attrs,
                        title
                    )(mkSpan(s, e))
                }
        }
        val plain = atomic(pos <~> (openDelim <* eolOrEof)).flatMap { case (s, _) =>
            (manyTill(
                compoundInnerBlock("--"),
                atomic(openDelim)
            ) <~> pos <* eolOrEof)
                .map { case (children, e) =>
                    CstDelimitedBlock(
                        DelimitedBlockKind.Open,
                        "--",
                        CstNestedContent(children)(mkSpan(s, e)),
                        None,
                        None
                    )(mkSpan(s, e))
                }
        }
        (withAttrs | plain).label("open block")

    // -----------------------------------------------------------------------
    // Attribute lists
    // -----------------------------------------------------------------------

    /** ADT for individual entries within an attribute list. Uses AST opaque types internally. */
    private enum AttrEntry:
        case Named(key: AttributeName, value: AttributeValue)
        case Opt(name: OptionName)
        case Role(name: RoleName)
        case Positional(value: AttributeValue)

    private val quotedValue: Parsley[String] =
        char('"') *> stringOfSome(satisfy(c => c != '"' && c != '\n' && c != '\r')) <* char('"')

    private val unquotedValue: Parsley[String] =
        stringOfSome(
            satisfy(c =>
                c != ',' && c != ']' && c != '"' && c != '=' && c != '\n' && c != '\r' && c != '%' && c != '#' && c != '.'
            )
        )

    private val shorthandChars = Set(',', ']', '%', '#', '.', '\n', '\r')

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
    val attributeListLine: Parsley[CstAttributeList] =
        (pos <~> (char('[') *> sepEndBy(attrEntry, option(char(',') *> option(char(' ')).void).void) <* char(
            ']'
        ) <* eolOrEof) <~> pos)
            .map { case ((s, entries), e) =>
                val positional = entries.collect { case AttrEntry.Positional(v) => v.value }.toList
                val named      = entries.collect { case AttrEntry.Named(k, v) => (k.value, v.value) }.toMap
                val options    = entries.collect { case AttrEntry.Opt(o) => o.value }.toList
                val roles      = entries.collect { case AttrEntry.Role(r) => r.value }.toList
                CstAttributeList(positional, named, options, roles)(mkSpan(s, e))
            }
            .label("attribute list")

    /** Parses zero or more attribute list lines and merges them into a single optional [[CstAttributeList]]. */
    val attributeLists: Parsley[Option[CstAttributeList]] =
        many(attributeListLine).map {
            case Nil => None
            case first :: rest =>
                Some(
                    rest.foldLeft(first)((acc, al) =>
                        CstAttributeList(
                            acc.positional ++ al.positional,
                            acc.named ++ al.named,
                            acc.options ++ al.options,
                            acc.roles ++ al.roles
                        )(AstSpan(acc.span.start, al.span.end))
                    )
                )
        }

    // -----------------------------------------------------------------------
    // Block title
    // -----------------------------------------------------------------------

    val blockTitle: Parsley[CstBlockTitle] =
        (pos <~> (char('.') *> notFollowedBy(char('.')) *> notFollowedBy(char(' ')) *> lineContent) <~> pos <* eolOrEof)
            .map { case ((s, content), e) => CstBlockTitle(content)(mkSpan(s, e)) }
            .label("block title")

    // -----------------------------------------------------------------------
    // Tables
    // -----------------------------------------------------------------------

    private val cellContent: Parsley[List[CstInline]] =
        many(
            boldSpan | constrainedBoldSpan | italicSpan | monoSpan |
                (pos <~> stringOfSome(satisfy(c => isContentChar(c) && c != '|')) <~> pos)
                    .map { case ((s, content), e) => CstText(content)(mkSpan(s, e)) } |
                (pos <~> unpairedMarkupChar <~> pos).map { case ((s, c), e) =>
                    CstText(c.toString)(mkSpan(s, e)): CstInline
                }
        )

    private def trimCellContent(content: List[CstInline]): List[CstInline] =
        content match
            case init :+ (t: CstText) =>
                val trimmed = t.content.stripTrailing()
                if trimmed.isEmpty then init
                else init :+ CstText(trimmed)(t.span)
            case other => other

    /** Parsed cell specifier using plain primitive types (not opaque). */
    private case class CellSpec(
        colSpan: Option[Int] = None,
        rowSpan: Option[Int] = None,
        dupFactor: Option[Int] = None,
        style: Option[Char] = None
    )

    private val cellSpecifier: Parsley[CellSpec] = atomic {
        import parsley.character.digit
        val digits  = some(digit).map(_.mkString.toInt)
        val styleOp = option(satisfy(c => "adehilms".contains(c)))

        val spanSpec = atomic {
            val colFactor = option(digits)
            val rowFactor = option(char('.') *> digits)
            (colFactor <~> rowFactor <* char('+') <~> styleOp <* char('|')).map { case ((col, row), sty) =>
                CellSpec(colSpan = col, rowSpan = row, style = sty)
            }
        }

        val dupSpec = atomic(
            (digits <* char('*') <~> styleOp <* char('|')).map { case (dup, sty) =>
                CellSpec(dupFactor = Some(dup), style = sty)
            }
        )

        val styleSpec = atomic((styleOp.filter(_.isDefined) <* char('|')).map(sty => CellSpec(style = sty)))

        spanSpec | dupSpec | styleSpec
    }

    private val tableCell: Parsley[CstTableCell] =
        val specifiedCell =
            (pos <~> cellSpecifier <~> option(char(' ')).void <~> cellContent <~> pos)
                .map { case ((((s, spec), _), content), e) =>
                    CstTableCell(
                        CstCellInlines(trimCellContent(content))(mkSpan(s, e)),
                        spec.style.map(_.toString),
                        spec.colSpan,
                        spec.rowSpan,
                        spec.dupFactor
                    )(mkSpan(s, e))
                }
        val plainCell =
            (pos <~> (char('|') *> option(char(' ')).void *> cellContent) <~> pos)
                .map { case ((s, content), e) =>
                    CstTableCell(CstCellInlines(trimCellContent(content))(mkSpan(s, e)), None, None, None, None)(
                        mkSpan(s, e)
                    )
                }
        specifiedCell | plainCell

    private val tableRowLine: Parsley[List[CstTableCell]] =
        some(tableCell).map(_.toList) <* eolOrEof

    private val tableRow: Parsley[CstTableRow] =
        (pos <~> tableRowLine <~> pos).map { case ((s, cells), e) => CstTableRow(cells)(mkSpan(s, e)) }

    private val tableRowsWithBlankTracking: Parsley[(List[CstTableRow], Boolean)] =
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

    private def csvRow(sep: Char): Parsley[List[CstTableCell]] =
        val unquoted = stringOfSome(satisfy(c => c != sep && c != '"' && c != '\n' && c != '\r'))
        val quoted   = char('"') *> many(satisfy(_ != '"') | (char('"') *> char('"'))).map(_.mkString) <* char('"')
        val cellVal  = option(quoted | unquoted).map(_.getOrElse("").trim)
        val cell = (pos <~> cellVal <~> pos).map { case ((s, v), e) =>
            val content: List[CstInline] =
                if v.isEmpty then Nil
                else List(CstText(v)(mkSpan(s, e)))
            CstTableCell(CstCellInlines(content)(mkSpan(s, e)), None, None, None, None)(mkSpan(s, e))
        }
        (cell <~> many(char(sep) *> cell)).map { case (first, rest) => first :: rest } <* eolOrEof

    private def dsvRow(sep: Char): Parsley[List[CstTableCell]] =
        val escaped = char('\\') *> satisfy(_ => true)
        val cellVal = many(escaped | satisfy(c => c != sep && c != '\n' && c != '\r')).map(_.mkString.trim)
        val cell = (pos <~> cellVal <~> pos).map { case ((s, v), e) =>
            val content: List[CstInline] =
                if v.isEmpty then Nil
                else List(CstText(v)(mkSpan(s, e)))
            CstTableCell(CstCellInlines(content)(mkSpan(s, e)), None, None, None, None)(mkSpan(s, e))
        }
        (cell <~> many(char(sep) *> cell)).map { case (first, rest) => first :: rest } <* eolOrEof

    private def dataTableRows(sep: Char, useCsv: Boolean, closingDelim: String): Parsley[List[CstTableRow]] =
        val rowParser = if useCsv then csvRow(sep) else dsvRow(sep)
        val row       = (pos <~> rowParser <~> pos).map { case ((s, cells), e) => CstTableRow(cells)(mkSpan(s, e)) }
        manyTill(
            row.map(Some(_)) <* option(some(blankLine)).void | blankLine.map(_ => None),
            atomic(string(closingDelim))
        ).map(_.flatten)

    private val nestedCellContent: Parsley[List[CstInline]] =
        many(
            boldSpan | constrainedBoldSpan | italicSpan | monoSpan |
                (pos <~> stringOfSome(satisfy(c => isContentChar(c) && c != '!')) <~> pos)
                    .map { case ((s, content), e) => CstText(content)(mkSpan(s, e)) } |
                (pos <~> unpairedMarkupChar <~> pos).map { case ((s, c), e) =>
                    CstText(c.toString)(mkSpan(s, e)): CstInline
                }
        )

    private val nestedTableCell: Parsley[CstTableCell] =
        (pos <~> (char('!') *> option(char(' ')).void *> nestedCellContent) <~> pos)
            .map { case ((s, content), e) =>
                CstTableCell(CstCellInlines(trimCellContent(content))(mkSpan(s, e)), None, None, None, None)(
                    mkSpan(s, e)
                )
            }

    private val nestedTableRowLine: Parsley[List[CstTableCell]] =
        some(nestedTableCell).map(_.toList) <* eolOrEof

    private val nestedTableRow: Parsley[CstTableRow] =
        (pos <~> nestedTableRowLine <~> pos).map { case ((s, cells), e) => CstTableRow(cells)(mkSpan(s, e)) }

    private val nestedTableRows: Parsley[List[CstTableRow]] =
        manyTill(
            nestedTableRow <* option(some(blankLine)).void,
            atomic(string("!==="))
        )

    val nestedTable: Parsley[CstBlock] =
        (pos <~> option(atomic(blockTitle)) <~> attributeLists <~>
            (atomic(string("!===")) <* eolOrEof) *>
            option(some(blankLine)).void *>
            nestedTableRows <~> pos <* eolOrEof)
            .map { case ((((s, title), attrs), rows), e) =>
                CstTable(rows, "!===", TableFormat.PSV, attrs, title, false)(mkSpan(s, e))
            }
            .label("nested table block")

    private val aCellBlock: Parsley[CstBlock] =
        nestedTable | listingBlock | literalBlock | commentBlock | passBlock |
            sidebarBlock | exampleBlock | quoteBlock | openBlock |
            unorderedList | orderedList |
            (pos <~> some(
                (pos <~> (notFollowedBy(char('|')) *> notFollowedBy(string("!===")) *>
                    notCstBlockStart *>
                    some(inlineElement) <* eolOrEof) <~> pos)
                    .map { case ((ls, content), le) => CstParagraphLine(content)(mkSpan(ls, le)) }
            ).map(_.toList) <~> pos)
                .map { case ((s, lines), e) => CstParagraph(lines)(mkSpan(s, e)) }

    val aCellContent: Parsley[List[CstBlock]] =
        manyTill(
            aCellBlock <* option(some(blankLine)).void,
            lookAhead(atomic(string("|==="))) | lookAhead(atomic(char('|')))
        )

    private val multiLineCell: Parsley[CstTableCell] =
        (pos <~> (notFollowedBy(string("|===")) *> char('|') *> option(char(' ')).void) *> aCellContent <~> pos)
            .map { case ((s, blocks), e) =>
                CstTableCell(CstCellBlocks(blocks)(mkSpan(s, e)), None, None, None, None)(mkSpan(s, e))
            }

    private val multiLineTableRow: Parsley[CstTableRow] =
        (pos <~> some(multiLineCell).map(_.toList) <~> pos)
            .map { case ((s, cells), e) => CstTableRow(cells)(mkSpan(s, e)) }

    private val multiLineTableRows: Parsley[List[CstTableRow]] =
        manyTill(
            multiLineTableRow <* option(some(blankLine)).void,
            atomic(string("|==="))
        )

    private def hasAStyleColumn(attrs: Option[CstAttributeList]): Boolean =
        attrs
            .map(_.named)
            .getOrElse(Map.empty)
            .get("cols")
            .exists(_.contains('a'))

    val tableBlock: Parsley[CstBlock] =
        val psvTable =
            (pos <~> option(atomic(blockTitle)) <~> attributeLists <~>
                (atomic(string("|===")) <* eolOrEof))
                .flatMap { case (((s, title), attrs), _) =>
                    option(some(blankLine)).void *> (
                        if hasAStyleColumn(attrs) then
                            (option(tableRowLine <* some(blankLine)).map(
                                _.map(cells => CstTableRow(cells)(mkSpan(s, s)))
                            ) <~> multiLineTableRows <~> pos <* eolOrEof)
                                .map { case ((headerRow, rows), e) =>
                                    val allRows            = headerRow.toList ++ rows
                                    val hasBlankAfterFirst = headerRow.isDefined
                                    CstTable(allRows, "|===", TableFormat.PSV, attrs, title, hasBlankAfterFirst)(
                                        mkSpan(s, e)
                                    )
                                }
                        else
                            (tableRowsWithBlankTracking <~> pos <* eolOrEof)
                                .map { case ((rows, hasBlankAfterFirst), e) =>
                                    val format = attrs
                                        .map(_.named)
                                        .getOrElse(Map.empty)
                                        .get("format") match
                                        case Some("csv") => TableFormat.CSV
                                        case Some("tsv") => TableFormat.TSV
                                        case Some("dsv") => TableFormat.DSV
                                        case _           => TableFormat.PSV
                                    CstTable(rows, "|===", format, attrs, title, hasBlankAfterFirst)(mkSpan(s, e))
                                }
                    )
                }

        val csvTable =
            (pos <~> option(atomic(blockTitle)) <~> attributeLists <~>
                (atomic(string(",===")) <* eolOrEof) *>
                option(some(blankLine)).void *>
                dataTableRows(',', true, ",===") <~> pos <* eolOrEof)
                .map { case ((((s, title), attrs), rows), e) =>
                    CstTable(rows, ",===", TableFormat.CSV, attrs, title, false)(mkSpan(s, e))
                }

        val dsvTable =
            (pos <~> option(atomic(blockTitle)) <~> attributeLists <~>
                (atomic(string(":===")) <* eolOrEof) *>
                option(some(blankLine)).void *>
                dataTableRows(':', false, ":===") <~> pos <* eolOrEof)
                .map { case ((((s, title), attrs), rows), e) =>
                    CstTable(rows, ":===", TableFormat.DSV, attrs, title, false)(mkSpan(s, e))
                }

        (psvTable | csvTable | dsvTable).label("table block")

    // -----------------------------------------------------------------------
    // New CST-only block parsers (not in original AST parser)
    // -----------------------------------------------------------------------

    /** Captures a single blank line as a [[CstBlankLine]] node. */
    val cstBlankLine: Parsley[CstBlankLine] =
        (pos <~> (hspaces *> eol) <~> pos)
            .map { case ((s, _), e) => CstBlankLine()(mkSpan(s, e)) }
            .label("blank line")

    /** Parses a single-line `// comment` as a [[CstLineComment]] node. */
    val lineCommentBlock: Parsley[CstBlock] =
        atomic(
            (pos <~> (string("//") *> many(nonEolChar).map(_.mkString)) <~> pos <* eolOrEof)
                .map { case ((s, content), e) => CstLineComment(content.stripPrefix(" "))(mkSpan(s, e)) }
        ).label("line comment")

    /** Parses an `include::target[attrs]` directive as a [[CstInclude]] node. */
    val includeDirective: Parsley[CstBlock] =
        atomic(
            (pos <~>
                (string("include::") *>
                    stringOfSome(satisfy(c => c != '[' && c != '\n' && c != '\r')) <~>
                    (char('[') *> many(satisfy(c => c != ']' && c != '\n')) <* char(']')).map(_.mkString)) <~>
                pos <* eolOrEof)
                .map { case ((s, (target, rawAttrs)), e) =>
                    val attrsSpan = mkSpan(s, e)
                    val attrs =
                        if rawAttrs.isEmpty then CstAttributeList.empty(attrsSpan)
                        else CstAttributeList(List(rawAttrs), Map.empty, Nil, Nil)(attrsSpan)
                    CstInclude(target, attrs)(mkSpan(s, e))
                }
        ).label("include directive")

    /** Parses a `:name: value` attribute entry as a [[CstAttributeEntry]] node. */
    val attributeEntryBlock: Parsley[CstBlock] =
        atomic(
            (pos <~>
                (char(':') *> stringOfSome(satisfy(c => c != ':' && c != '\n' && c != '\r')) <* char(':') <* option(
                    char(' ')
                )) <~>
                many(nonEolChar).map(_.mkString) <~> pos <* eolOrEof)
                .map { case (((s, name), value), e) => CstAttributeEntry(name, value)(mkSpan(s, e)) }
        ).label("attribute entry")

    // -----------------------------------------------------------------------
    // Paragraphs
    // -----------------------------------------------------------------------

    /** Negative lookahead for any block-starting prefix, extended to reject CST-only block starters. */
    private val notCstBlockStart: Parsley[Unit] =
        notFollowedBy(headingLevel *> char(' ')) *>
            notFollowedBy(char('*') *> char(' ')) *>
            notFollowedBy(char('.') *> char(' ')) *>
            notFollowedBy(char('.') *> satisfy(c => c != '.' && c != ' ' && c != '\n' && c != '\r')) *>
            notFollowedBy(char('[') *> satisfy(c => c != '\n' && c != '\r')) *>
            notFollowedBy(string("----")) *>
            notFollowedBy(string("....")) *>
            notFollowedBy(string("****")) *>
            notFollowedBy(string("====")) *>
            notFollowedBy(string("____")) *>
            notFollowedBy(string("////")) *>
            notFollowedBy(string("++++")) *>
            notFollowedBy(string("--") <* notFollowedBy(char('-'))) *>
            notFollowedBy(string("|===")) *>
            notFollowedBy(string("!===")) *>
            notFollowedBy(string(",===")) *>
            notFollowedBy(string(":===")) *>
            notFollowedBy(string("//")) *>                                  // line comment
            notFollowedBy(string("include::")) *>                           // include directive
            notFollowedBy(char(':') *> satisfy(c => c != ':' && c != '\n')) // attribute entry

    private val cstParagraphLine: Parsley[CstParagraphLine] =
        (pos <~> (notCstBlockStart *> some(inlineElement) <* eolOrEof) <~> pos)
            .map { case ((s, content), e) => CstParagraphLine(content)(mkSpan(s, e)) }
            .label("paragraph line")

    val paragraph: Parsley[CstBlock] =
        (pos <~> some(cstParagraphLine).map(_.toList) <~> pos)
            .map { case ((s, lines), e) => CstParagraph(lines)(mkSpan(s, e)) }
            .label("paragraph")

    // -----------------------------------------------------------------------
    // Top-level block combinator
    // -----------------------------------------------------------------------

    /** Recognises any one block, trying block types in priority order. */
    private[parser] val block: Parsley[CstBlock] =
        listingBlock | literalBlock | commentBlock | passBlock |
            sidebarBlock | exampleBlock | quoteBlock | openBlock |
            tableBlock | heading | unorderedList | orderedList |
            lineCommentBlock | includeDirective | attributeEntryBlock |
            paragraph
