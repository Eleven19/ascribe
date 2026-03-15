package io.github.eleven19.ascribe

import parsley.Result

import io.github.eleven19.ascribe.ast.Document
import io.github.eleven19.ascribe.parser.DocumentParser

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
      * @param source
      *   the raw AsciiDoc text (UTF-8 string)
      * @return
      *   [[parsley.Success]] wrapping the [[Document]], or [[parsley.Failure]] with a human-readable error message
      */
    def parse(source: String): Result[String, Document] =
        DocumentParser.document.parse(source)
