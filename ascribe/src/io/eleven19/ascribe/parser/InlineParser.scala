package io.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.{atomic, empty, eof, lookAhead, many, notFollowedBy, pure, some, unit}
import parsley.character.{char, satisfy, string, stringOfMany, stringOfSome}
import parsley.combinator.manyTill
import parsley.errors.combinator.ErrorMethods
import parsley.position.pos
import parsley.state.Ref

import io.eleven19.ascribe.ast.{Span, mkSpan}
import io.eleven19.ascribe.cst.{
    CstAttributeRef,
    CstAutolink,
    CstBold,
    CstInline,
    CstItalic,
    CstLinkMacro,
    CstMailtoMacro,
    CstMono,
    CstText,
    CstUrlMacro
}
import io.eleven19.ascribe.lexer.AsciiDocLexer.*

/** Parsers for inline AsciiDoc elements.
  *
  * Inline content sits inside headings, paragraphs, and list items. This module handles unconstrained markup spans
  * (double-delimiter pairs) and falls back to plain text for everything else. Each parser emits a [[CstInline]] node.
  *
  * ==Supported markup==
  *   - `**bold**` -- [[CstBold]] (constrained=false)
  *   - `*bold*` -- [[CstBold]] (constrained=true)
  *   - `__italic__` -- [[CstItalic]]
  *   - ` ``mono`` ` -- [[CstMono]]
  *   - everything else -- [[CstText]]
  *
  * ==Error handling==
  * Every parser carries a `.label` for "expected" messages and, where helpful, an `.explain` that describes the correct
  * syntax to the user.
  */
object InlineParser:

    // -----------------------------------------------------------------------
    // Delimited span helpers
    // -----------------------------------------------------------------------

    /** Parses a delimited inline span.
      *
      * Consumes the `open` delimiter, then all [[nonEolChar]] characters until the `close` delimiter is consumed,
      * returning the collected characters as a `String`. The entire parser is wrapped in [[atomic]] so that a missing
      * closing delimiter causes clean backtracking to before the opening delimiter.
      */
    private def delimitedContent(open: String, close: String): Parsley[String] =
        atomic(string(open) *> manyTill(nonEolChar, string(close))).map(_.mkString)

    // -----------------------------------------------------------------------
    // Word-boundary tracking for constrained formatting
    // -----------------------------------------------------------------------

    private val lastChar: Ref[Option[Char]] = Ref.make[Option[Char]]

    private def isWordChar(c: Char): Boolean = c.isLetterOrDigit || c == '_'

    private val atConstrainedOpen: Parsley[Unit] =
        lastChar.get.flatMap {
            case None                      => unit
            case Some(c) if !isWordChar(c) => unit
            case _                         => empty
        }

    private val atConstrainedClose: Parsley[Unit] =
        lookAhead(satisfy(c => !c.isLetterOrDigit)).void | eof

    /** Resets the `lastChar` state to `None`. Useful when starting a new line of inline content. */
    val resetLastChar: Parsley[Unit] = lastChar.set(None)

    // -----------------------------------------------------------------------
    // Inline element parsers
    // -----------------------------------------------------------------------

    /** Parses an unconstrained **bold** span: `**content**`. */
    val boldSpan: Parsley[CstInline] =
        (pos <~> delimitedContent("**", "**") <~> pos)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                CstBold(List(CstText(content)(span)), constrained = false)(span)
            }
            .flatMap(node => lastChar.set(Some('*')) *> pure(node: CstInline))
            .label("bold span")
            .explain("Bold text is surrounded by double asterisks, e.g. **bold**")

    /** Parses an unconstrained _italic_ span: `__content__`. */
    val italicSpan: Parsley[CstInline] =
        (pos <~> delimitedContent("__", "__") <~> pos)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                CstItalic(List(CstText(content)(span)), constrained = false)(span)
            }
            .flatMap(node => lastChar.set(Some('_')) *> pure(node: CstInline))
            .label("italic span")
            .explain("Italic text is surrounded by double underscores, e.g. __italic__")

    /** Parses an unconstrained `monospace` span: ` ``content`` `. */
    val monoSpan: Parsley[CstInline] =
        (pos <~> delimitedContent("``", "``") <~> pos)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                CstMono(List(CstText(content)(span)), constrained = false)(span)
            }
            .flatMap(node => lastChar.set(Some('`')) *> pure(node: CstInline))
            .label("monospace span")
            .explain("Monospace text is surrounded by double backticks, e.g. ``mono``")

    /** Parses an attribute reference: `{name}` where name starts with a letter. */
    val attrRefInline: Parsley[CstInline] =
        atomic(
            (pos <~>
                (char('{') *>
                    (satisfy(_.isLetter) <~> stringOfMany(satisfy(c => c.isLetterOrDigit || c == '-' || c == '_')))
                        .map { case (first, rest) => first.toString + rest } <*
                    char('}')) <~>
                pos)
                .map { case ((s, name), e) => CstAttributeRef(name)(mkSpan(s, e)) }
        ).flatMap(node => lastChar.set(Some('}')) *> pure(node: CstInline))
            .label("attribute reference")

    /** Parses a constrained *bold* span: `*content*`.
      *
      * Must be tried after unconstrained `**` to avoid ambiguity. Requires non-word char (or SOL) before opening and
      * space/punct/EOL after closing.
      */
    val constrainedBoldSpan: Parsley[CstInline] =
        atomic(atConstrainedOpen *> (pos <~> delimitedContent("*", "*") <~> pos) <* atConstrainedClose)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                CstBold(List(CstText(content)(span)), constrained = true)(span)
            }
            .flatMap(node => lastChar.set(Some('*')) *> pure(node: CstInline))
            .label("constrained bold span")

    /** Parses a constrained _italic_ span: `_content_`.
      *
      * Must be tried after unconstrained `__` to avoid ambiguity. Requires non-word char (or SOL) before opening and
      * space/punct/EOL after closing.
      */
    val constrainedItalicSpan: Parsley[CstInline] =
        atomic(atConstrainedOpen *> (pos <~> delimitedContent("_", "_") <~> pos) <* atConstrainedClose)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                CstItalic(List(CstText(content)(span)), constrained = true)(span)
            }
            .flatMap(node => lastChar.set(Some('_')) *> pure(node: CstInline))
            .label("constrained italic span")

    /** Parses a constrained `mono` span: `` `content` ``.
      *
      * Must be tried after unconstrained ` `` ` to avoid ambiguity. Requires non-word char (or SOL) before opening and
      * space/punct/EOL after closing.
      */
    val constrainedMonoSpan: Parsley[CstInline] =
        atomic(atConstrainedOpen *> (pos <~> delimitedContent("`", "`") <~> pos) <* atConstrainedClose)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                CstMono(List(CstText(content)(span)), constrained = true)(span)
            }
            .flatMap(node => lastChar.set(Some('`')) *> pure(node: CstInline))
            .label("constrained monospace span")

    // -----------------------------------------------------------------------
    // Link and URL parsers
    // -----------------------------------------------------------------------

    /** Characters valid in a macro target (everything up to `[`). */
    private val macroTargetChars: Parsley[String] =
        stringOfSome(satisfy(c => c != '[' && c != '\n' && c != '\r' && c != ' ' && c != '\t'))

    /** Parses inline content between `[` and `]` for link text. */
    private val bracketedInlineContent: Parsley[List[CstInline]] =
        char('[') *> many(
            boldSpan | italicSpan | monoSpan |
                constrainedBoldSpan | constrainedItalicSpan | constrainedMonoSpan |
                attrRefInline |
                (pos <~> stringOfSome(
                    satisfy(c =>
                        c != ']' && c != '\n' && c != '\r' && c != '*' && c != '_' && c != '`' && c != '{' && c != '}'
                    )
                ) <~> pos)
                    .map { case ((s, content), e) => CstText(content)(mkSpan(s, e)): CstInline }
        ) <* char(']')

    /** Parses `link:target[text]`. */
    val linkMacro: Parsley[CstInline] =
        atomic(
            (pos <~> (string("link:") *> macroTargetChars) <~> bracketedInlineContent <~> pos)
                .map { case (((s, target), text), e) =>
                    CstLinkMacro(target, text)(mkSpan(s, e))
                }
        ).flatMap(node => lastChar.set(Some(']')) *> pure(node: CstInline))
            .label("link macro")
            .explain("Link macro syntax: link:target[text]")

    /** Parses `mailto:addr[text]`. */
    val mailtoMacro: Parsley[CstInline] =
        atomic(
            (pos <~> (string("mailto:") *> macroTargetChars) <~> bracketedInlineContent <~> pos)
                .map { case (((s, target), text), e) =>
                    CstMailtoMacro(target, text)(mkSpan(s, e))
                }
        ).flatMap(node => lastChar.set(Some(']')) *> pure(node: CstInline))
            .label("mailto macro")
            .explain("Mailto macro syntax: mailto:address[text]")

    /** Recognized URL schemes for autolinks and URL macros. */
    private val urlScheme: Parsley[String] =
        atomic(string("https://")) | atomic(string("http://")) | atomic(string("ftp://")) | atomic(string("irc://"))

    /** Characters valid in a URL (after the scheme). Stops at whitespace, `[`, `]`, newlines. */
    private val urlChars: Parsley[String] =
        stringOfSome(satisfy(c => c != ' ' && c != '\t' && c != '\n' && c != '\r' && c != '[' && c != ']'))

    /** Parses a URL macro: `scheme://target[text]`. */
    val urlMacro: Parsley[CstInline] =
        atomic(
            (pos <~> (urlScheme <~> urlChars).map(_ + _) <~> bracketedInlineContent <~> pos)
                .map { case (((s, target), text), e) =>
                    CstUrlMacro(target, text)(mkSpan(s, e))
                }
        ).flatMap(node => lastChar.set(Some(']')) *> pure(node: CstInline))
            .label("URL macro")

    /** Parses a bare autolink: `scheme://target` (not followed by `[`). Trailing punctuation stripping is deferred to a
      * follow-up issue.
      */
    val autolink: Parsley[CstInline] =
        atomic(
            (pos <~> (urlScheme <~> urlChars).map(_ + _) <~> pos)
                .map { case ((s, target), e) =>
                    CstAutolink(target)(mkSpan(s, e))
                }
        ).flatMap { node =>
            val al = node.asInstanceOf[CstAutolink]
            lastChar.set(al.target.lastOption) *> pure(node: CstInline)
        }.label("autolink")

    /** Lookahead that recognises the start of a link or URL prefix within prose. */
    private val linkOrUrlPrefix: Parsley[Unit] =
        (atomic(string("link:")) | atomic(string("mailto:")) | atomic(string("https://")) | atomic(
            string("http://")
        ) | atomic(string("ftp://")) | atomic(string("irc://"))).void

    /** Parses one or more unadorned prose characters as a [[CstText]] node.
      *
      * Stops before any character sequence that could begin a link macro or URL so that the higher-priority link
      * parsers get a chance to match.
      */
    val plainTextInline: Parsley[CstInline] =
        (pos <~> some(notFollowedBy(linkOrUrlPrefix) *> contentChar).map(_.mkString) <~> pos)
            .map { case ((s, content), e) => CstText(content)(mkSpan(s, e)) }
            .flatMap { node =>
                val text = node.asInstanceOf[CstText]
                lastChar.set(text.content.lastOption) *> pure(node: CstInline)
            }
            .label("text")

    /** Fallback for a single markup character that did not open a valid span. */
    val unpairedMarkupInline: Parsley[CstInline] =
        (pos <~> unpairedMarkupChar <~> pos)
            .map { case ((s, c), e) => (CstText(c.toString)(mkSpan(s, e)), c) }
            .flatMap { case (node, c) =>
                lastChar.set(Some(c)) *> pure(node: CstInline)
            }
            .hide

    /** Parses a single inline element (one of the above parsers in priority order). Unconstrained (`**`) is tried
      * before constrained (`*`) to avoid ambiguity.
      */
    val inlineElement: Parsley[CstInline] =
        boldSpan | italicSpan | monoSpan |
            constrainedBoldSpan | constrainedItalicSpan | constrainedMonoSpan |
            linkMacro | mailtoMacro | urlMacro | autolink |
            attrRefInline | plainTextInline | unpairedMarkupInline

    /** Parses zero or more inline elements, stopping naturally at a newline or end-of-input. */
    val lineContent: Parsley[List[CstInline]] = lastChar.set(None) *> many(inlineElement)
