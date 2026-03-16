package io.github.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.eof
import parsley.combinator.{option, sepEndBy, some}

import io.github.eleven19.ascribe.ast.Document
import io.github.eleven19.ascribe.lexer.AsciiDocLexer.blankLine
import io.github.eleven19.ascribe.parser.BlockParser.*

/** Top-level parser for a complete AsciiDoc document.
  *
  * A document is a sequence of [[io.github.eleven19.ascribe.ast.Block]] elements separated (and optionally terminated)
  * by blank lines. Leading blank lines are tolerated and silently discarded.
  *
  * {{{
  * val result = DocumentParser.document.parse(source)
  * result match
  *   case parsley.Success(doc)     => // work with Document
  *   case parsley.Failure(message) => // report parse error
  * }}}
  */
object DocumentParser:

    /** One or more consecutive blank lines used as a block separator. */
    private val blankLines: Parsley[Unit] = some(blankLine).void

    /** Recognises any one block, trying block types in priority order:
      *
      *   1. [[heading]] -- distinguished by leading `=` marker
      *   2. [[unorderedList]] -- distinguished by leading `* `
      *   3. [[orderedList]] -- distinguished by leading `. `
      *   4. [[paragraph]] -- everything else (last resort)
      */
    private val block: Parsley[io.github.eleven19.ascribe.ast.Block] =
        heading | unorderedList | orderedList | paragraph

    /** Parses a complete AsciiDoc document from start to end of input. The Document bridge constructor captures position
      * before and after parsing.
      *
      * Blocks are separated by [[blankLines]]; any leading blank lines before the first block are discarded. Parsing
      * fails if any input remains after the final block.
      */
    val document: Parsley[Document] =
        Document(option(blankLines) *> sepEndBy(block, blankLines).map(_.toList) <* eof)
