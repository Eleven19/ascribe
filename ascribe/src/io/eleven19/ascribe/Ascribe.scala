package io.eleven19.ascribe

import parsley.Result

import io.eleven19.ascribe.ast.Document
import io.eleven19.ascribe.cst.{CstDocument, CstLowering}
import io.eleven19.ascribe.parser.DocumentParser

/** Public API for the Ascribe AsciiDoc parser.
  *
  * {{{
  * Ascribe.parse("= Hello World\n\nFirst paragraph.\n") match
  *   case parsley.Success(doc)     => println(doc)
  *   case parsley.Failure(message) => println(s"Error: \$message")
  * }}}
  */
object Ascribe:

    /** Parses an AsciiDoc source string into a [[Document]].
      *
      * Internally calls [[parseCst]] and lowers the result via [[CstLowering.toAst]].
      *
      * @param source
      *   the raw AsciiDoc text (UTF-8 string)
      * @return
      *   [[parsley.Success]] wrapping the [[Document]], or [[parsley.Failure]] with a human-readable error message
      */
    def parse(source: String): Result[String, Document] =
        parseCst(source).map(CstLowering.toAst)

    /** Parses an AsciiDoc source string into a [[CstDocument]], preserving full source structure including include
      * directives, comments, blank lines, and individual paragraph line boundaries.
      *
      * @param source
      *   the raw AsciiDoc text (UTF-8 string)
      * @return
      *   [[parsley.Success]] wrapping the [[CstDocument]], or [[parsley.Failure]] with a human-readable error message
      */
    def parseCst(source: String): Result[String, CstDocument] =
        DocumentParser.document.parse(source)
