package io.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.{atomic, eof, many, some}
import parsley.character.{char, satisfy, string, stringOfSome}
import parsley.combinator.option
import parsley.position.pos

import io.eleven19.ascribe.ast.mkSpan
import io.eleven19.ascribe.cst.*
import io.eleven19.ascribe.lexer.AsciiDocLexer.{blankLine, eolOrEof, nonEolChar}
import io.eleven19.ascribe.parser.BlockParser.*
import io.eleven19.ascribe.parser.InlineParser.*

/** Top-level parser for a complete AsciiDoc document.
  *
  * A document is an optional header followed by a sequence of [[CstTopLevel]] elements. Blank lines, single-line
  * comments, include directives, and attribute entries are all captured as first-class CST nodes.
  *
  * After parsing, the CST preserves the full source structure. Use [[CstLowering.toAst]] to obtain the
  * [[io.eleven19.ascribe.ast.Document]] representation.
  */
object DocumentParser:

    /** One or more consecutive blank lines used as a block separator (consumed, not captured). */
    private val blankLines: Parsley[Unit] = some(blankLine).void

    /** A top-level item: blank line, line comment, include directive, attribute entry, or any block. */
    private val cstTopLevelItem: Parsley[CstTopLevel] =
        cstBlankLine | lineCommentBlock | includeDirective | attributeEntryBlock | block

    /** Parses `:key: value` lines inside a document header. */
    private val headerAttributeEntry: Parsley[CstAttributeEntry] =
        (pos <~>
            (char(':') *> stringOfSome(satisfy(c => c != ':' && c != '\n' && c != '\r')) <* char(':') <* option(
                char(' ')
            )) <~>
            many(nonEolChar).map(_.mkString) <~> pos <* eolOrEof)
            .map { case (((s, name), value), e) => CstAttributeEntry(name, value, false)(mkSpan(s, e)) }

    /** Parses a document header: `= Title` followed by optional attribute entries. */
    private val documentHeader: Parsley[CstDocumentHeader] =
        (pos <~> (atomic(string("= ")) *> lineContent <~> pos <* eolOrEof) <~>
            many(headerAttributeEntry))
            .map { case ((s, (title, titleEndPos)), attrs) =>
                val headingSpan = mkSpan(s, titleEndPos)
                val endPos: (Int, Int) = attrs.lastOption
                    .map(a => (a.span.end.line, a.span.end.col))
                    .getOrElse(titleEndPos)
                val heading = CstHeading(1, "=", title)(headingSpan)
                CstDocumentHeader(heading, attrs)(mkSpan(s, endPos))
            }

    /** Parses a complete AsciiDoc document from start to end of input, emitting a [[CstDocument]]. */
    val document: Parsley[CstDocument] =
        (pos <~>
            option(blankLines) *> option(documentHeader <* option(blankLines)) <~>
            many(cstTopLevelItem) <* eof <~> pos)
            .map { case (((s, header), content), e) =>
                CstDocument(header, content)(mkSpan(s, e))
            }
