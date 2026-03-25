package io.eleven19.ascribe.lexer

import parsley.Parsley
import parsley.Parsley.{eof, many}
import parsley.character.{char, satisfy, stringOfSome}
import parsley.combinator.option
import parsley.errors.combinator.ErrorMethods
import parsley.token.Lexer
import parsley.token.descriptions.{LexicalDesc, NameDesc, SymbolDesc}
import parsley.token.predicate

/** Provides tokenisation primitives for AsciiDoc content.
  *
  * Parsley's [[Lexer]] is used to configure word recognition (via [[LexicalDesc]]) and to ensure that keyword/operator
  * boundaries are respected. Raw character-level parsers handle the newline-sensitive, line-oriented aspects of the
  * format.
  */
object AsciiDocLexer:

    // -----------------------------------------------------------------------
    // Token Lexer configuration
    // -----------------------------------------------------------------------

    /** Lexical description for AsciiDoc "word" tokens.
      *
      * Identifiers (words) start with a letter or digit and may contain letters, digits, hyphens, apostrophes, dots,
      * colons, or slashes – common in prose, URLs, and attribute values. Whitespace is limited to horizontal characters
      * only; newlines are structurally significant in AsciiDoc and must **not** be skipped by the lexer.
      */
    private val desc: LexicalDesc = LexicalDesc.plain.copy(
        nameDesc = NameDesc.plain.copy(
            identifierStart = predicate.Basic(c => c.isLetter || c.isDigit),
            identifierLetter =
                predicate.Basic(c => c.isLetterOrDigit || c == '-' || c == '\'' || c == '.' || c == ':' || c == '/')
        ),
        symbolDesc = SymbolDesc.plain.copy(
            hardKeywords = Set.empty,
            hardOperators = Set("**", "__", "``", "=====", "====", "===", "==", "=")
        )
    )

    private val lexer: Lexer = new Lexer(desc)

    /** Parses a single word (identifier-like token), consuming trailing horizontal whitespace. */
    val word: Parsley[String] = lexer.lexeme.names.identifier

    /** Fully wraps a parser: skips leading whitespace and asserts end-of-input at the end. */
    def fully[A](p: Parsley[A]): Parsley[A] = lexer.fully(p)

    // -----------------------------------------------------------------------
    // Line-level character primitives
    // -----------------------------------------------------------------------

    /** Matches a single space or tab (horizontal whitespace – does **not** match newlines). */
    val hspace: Parsley[Char] = satisfy(c => c == ' ' || c == '\t')

    /** Skips zero or more horizontal whitespace characters. */
    val hspaces: Parsley[Unit] = many(hspace).void

    /** Matches an end-of-line sequence (`\n` or `\r\n`) and discards it. */
    val eol: Parsley[Unit] = (option(char('\r')) *> char('\n')).void

    /** Matches a blank line (optional horizontal whitespace then EOL). */
    val blankLine: Parsley[Unit] = (hspaces *> eol).void.label("blank line")

    /** End of line **or** end of input – used for the final line of a document. */
    val eolOrEof: Parsley[Unit] = eol | eof

    // -----------------------------------------------------------------------
    // Content character classes
    // -----------------------------------------------------------------------

    /** Returns `true` for characters that may appear as unadorned prose text.
      *
      * Excluded: control characters, newlines, and the four inline markup delimiters (`*`, `_`, `` ` ``, `\`).
      */
    def isContentChar(c: Char): Boolean =
        !c.isControl && c != '\n' && c != '\r' &&
            c != '*' && c != '_' && c != '`' &&
            c != '{' && c != '}'

    /** Parses a single unadorned prose character. */
    val contentChar: Parsley[Char] = satisfy(isContentChar)

    /** Parses one or more unadorned prose characters as a string. */
    val plainText: Parsley[String] = stringOfSome(contentChar)

    /** Parses any character that is not a newline (used inside delimited spans). */
    val nonEolChar: Parsley[Char] = satisfy(c => c != '\n' && c != '\r')

    /** Parses a single inline-markup character (`*`, `_`, or `` ` ``) that is not a newline. Used as a fallback when a
      * delimiter sequence does not open a valid span.
      */
    val unpairedMarkupChar: Parsley[Char] =
        satisfy(c => (c == '*' || c == '_' || c == '`' || c == '{' || c == '}') && c != '\n')
