package io.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.eof
import parsley.combinator.{option, sepEndBy, some}

import io.eleven19.ascribe.ast.{Block, Document, Heading, Section, Span}
import io.eleven19.ascribe.lexer.AsciiDocLexer.blankLine
import io.eleven19.ascribe.parser.BlockParser.*

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

    /** Recognises any one block, trying block types in priority order. */
    private val block: Parsley[Block] =
        listingBlock | heading | unorderedList | orderedList | paragraph

    /** Parses a complete AsciiDoc document from start to end of input. After parsing, headings are restructured into
      * sections that contain their subsequent blocks.
      */
    val document: Parsley[Document] =
        Document(option(blankLines) *> sepEndBy(block, blankLines).map(blocks => restructure(blocks.toList)) <* eof)

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
