package io.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.{atomic, eof}
import parsley.character.{char, noneOf, satisfy, string, stringOfSome}
import parsley.combinator.{many, option, sepEndBy, some}
import parsley.position.pos

import io.eleven19.ascribe.ast.{Block, Document, DocumentHeader, Heading, Section, Span, mkSpan}
import io.eleven19.ascribe.lexer.AsciiDocLexer.{blankLine, eol, eolOrEof, hspaces, nonEolChar}
import io.eleven19.ascribe.parser.BlockParser.*
import io.eleven19.ascribe.parser.InlineParser.*

/** Top-level parser for a complete AsciiDoc document.
  *
  * A document is a sequence of [[io.eleven19.ascribe.ast.Block]] elements separated (and optionally terminated) by
  * blank lines. Leading blank lines are tolerated and silently discarded.
  *
  * After parsing, headings are restructured into [[Section]] containers that collect subsequent blocks.
  */
object DocumentParser:

    /** One or more consecutive blank lines used as a block separator. */
    private val blankLines: Parsley[Unit] = some(blankLine).void

    /** Recognises any one block, trying block types in priority order. Delimited blocks are tried before headings since
      * `====` could be either an example block delimiter or a heading level 4 marker (heading requires a space after
      * the equals signs).
      */
    private val block: Parsley[Block] =
        listingBlock | literalBlock | commentBlock | passBlock |
            sidebarBlock | exampleBlock | quoteBlock | openBlock |
            tableBlock | heading | unorderedList | orderedList | paragraph

    /** Parses an attribute entry line: `:key: value` or `:key:` (empty value). Returns (key, value, endPos). */
    private val attributeEntry: Parsley[(String, String, (Int, Int))] =
        ((char(':') *> stringOfSome(satisfy(c => c != ':' && c != '\n' && c != '\r')) <*
            char(':') <* option(char(' '))) <~>
            many(nonEolChar).map(_.mkString) <~> pos <* eolOrEof)
            .map { case ((k, v), p) => (k, v, p) }

    /** Parses a document header: `= Title` followed by optional attribute entries. */
    private val documentHeader: Parsley[DocumentHeader] =
        (pos <~> (atomic(string("= ")) *> lineContent <~> pos <* eolOrEof) <~>
            many(attributeEntry))
            .map { case ((s, (title, titleEndPos)), attrs) =>
                // Header span ends at the last attribute's end position, or at the title end
                val endPos = attrs.lastOption.map(_._3).getOrElse(titleEndPos)
                DocumentHeader(title, attrs.map((k, v, _) => (k, v)))(mkSpan(s, endPos))
            }

    /** Parses a complete AsciiDoc document from start to end of input. */
    val document: Parsley[Document] =
        (pos <~> option(blankLines) *> option(documentHeader <* option(blankLines)) <~>
            sepEndBy(block, blankLines).map(blocks => restructure(blocks.toList)) <* eof <~> pos)
            .map { case (((s, header), blocks), e) =>
                Document(header, blocks)(mkSpan(s, e))
            }

    /** Restructure a flat list of blocks: convert each Heading (level >= 2) into a Section that contains all subsequent
      * blocks until the next heading of equal or lesser level, or end of list. Level 1 headings (single `=`) are
      * document titles and are NOT restructured into sections.
      */
    private def restructure(blocks: List[Block]): List[Block] =
        blocks match
            case Nil => Nil
            case (h: Heading) :: rest if h.level >= 2 =>
                val sectionLevel = h.level - 1 // ASG level: == is level 1, === is level 2, etc.
                val (nested, remaining) = rest.span {
                    case hh: Heading if hh.level <= h.level => false
                    case _                                  => true
                }
                val sectionSpan = nested.lastOption.map(_.span).getOrElse(h.span)
                val section = Section(sectionLevel, h.title, restructure(nested))(
                    Span(h.span.start, sectionSpan.end)
                )
                section :: restructure(remaining)
            case head :: rest =>
                head :: restructure(rest)
