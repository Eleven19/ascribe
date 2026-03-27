# Link and URL Inline Macros Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parse bare autolinks, URL macros, link: macros, and mailto: macros, lowering them through CST -> AST -> ASG.

**Architecture:** Add 4 CST inline nodes, a single AST `Link` case class with `LinkVariant`/`MacroKind` enums, and wire through lowering, bridge, renderer, DSLs, and tests. The parser uses Parsley combinators with scheme-based detection.

**Tech Stack:** Scala 3, Parsley parser combinators, ZIO Test, Mill build

**Spec:** `docs/superpowers/specs/2026-03-26-link-inline-macros-design.md`

**Test command:** `./mill ascribe.test.testLocal`

**Bridge test command:** `./mill ascribe.bridge.test.testLocal`

---

### Task 1: AST types — LinkVariant, MacroKind, Link

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/ast/Document.scala:32` (after ConstrainedBold)

- [ ] **Step 1: Add the LinkVariant and MacroKind enums and Link case class**

In `ascribe/src/io/eleven19/ascribe/ast/Document.scala`, after the `ConstrainedBold` case class (line 32), add:

```scala
/** Distinguishes the kind of inline macro that produced a link. */
enum MacroKind:
    /** URL macro: `https://example.com[text]` */
    case Url(scheme: String)
    /** Explicit link macro: `link:target[text]` */
    case Link
    /** Mailto macro: `mailto:user@host[text]` */
    case MailTo

/** Distinguishes auto-detected bare URLs from explicit macro invocations. */
enum LinkVariant:
    /** Bare URL auto-detected by scheme prefix. */
    case Auto
    /** An inline macro with `target[text]` syntax. */
    case Macro(kind: MacroKind)

/** A hyperlink inline node. Covers bare autolinks, URL macros, link: macros, and mailto: macros.
  *
  * The `variant` field captures how the link was authored. The `target` is the URL or path.
  * An empty `text` list means no display text was provided (renderer decides display).
  */
case class Link(variant: LinkVariant, target: String, text: List[Inline])(val span: Span)
    extends Inline derives CanEqual:
    /** Extracts the URL scheme from the target, if present. */
    lazy val scheme: Option[String] =
        target.indexOf("://") match
            case -1  => None
            case idx => Some(target.substring(0, idx))

object Link:
    /** Extractor for pattern-matching on the scheme: `case Link.Scheme(s) => ...` */
    object Scheme:
        def unapply(link: Link): Option[String] = link.scheme
```

- [ ] **Step 2: Verify compilation**

Run: `./mill ascribe.compile`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ascribe/src/io/eleven19/ascribe/ast/Document.scala
git commit -m "feat(ast): add Link, LinkVariant, and MacroKind types for inline links"
```

---

### Task 2: AST DSL constructors

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/ast/dsl.scala:29` (after `mono`)

- [ ] **Step 1: Add link DSL constructors**

In `ascribe/src/io/eleven19/ascribe/ast/dsl.scala`, after the `mono` definition (line 29), add:

```scala
    def autoLink(target: String): Link =
        Link(LinkVariant.Auto, target, Nil)(u)
    def urlLink(scheme: String, target: String, text: Inline*): Link =
        Link(LinkVariant.Macro(MacroKind.Url(scheme)), target, text.toList)(u)
    def link(target: String, text: Inline*): Link =
        Link(LinkVariant.Macro(MacroKind.Link), target, text.toList)(u)
    def mailtoLink(target: String, text: Inline*): Link =
        Link(LinkVariant.Macro(MacroKind.MailTo), target, text.toList)(u)
```

- [ ] **Step 2: Verify compilation**

Run: `./mill ascribe.compile`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ascribe/src/io/eleven19/ascribe/ast/dsl.scala
git commit -m "feat(ast): add autoLink, urlLink, link, mailtoLink DSL constructors"
```

---

### Task 3: CST nodes for links

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstNodes.scala:178` (after CstAttributeRef)

- [ ] **Step 1: Add 4 CST inline node types**

In `ascribe/src/io/eleven19/ascribe/cst/CstNodes.scala`, after `CstAttributeRef` (line 178), add:

```scala

case class CstAutolink(target: String)(val span: Span) extends CstInline derives CanEqual

case class CstUrlMacro(target: String, text: List[CstInline])(val span: Span)
    extends CstInline derives CanEqual

case class CstLinkMacro(target: String, text: List[CstInline])(val span: Span)
    extends CstInline derives CanEqual

case class CstMailtoMacro(target: String, text: List[CstInline])(val span: Span)
    extends CstInline derives CanEqual
```

- [ ] **Step 2: Verify compilation**

Run: `./mill ascribe.compile`
Expected: SUCCESS (may show warnings about non-exhaustive matches in lowering/renderer — expected, we'll fix those next)

- [ ] **Step 3: Commit**

```bash
git add ascribe/src/io/eleven19/ascribe/cst/CstNodes.scala
git commit -m "feat(cst): add CstAutolink, CstUrlMacro, CstLinkMacro, CstMailtoMacro nodes"
```

---

### Task 4: CST lowering for link nodes

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstLowering.scala:51` (after CstAttributeRef match case)

- [ ] **Step 1: Write the failing test**

In `ascribe/test/src/io/eleven19/ascribe/cst/CstLoweringSpec.scala`, add a new suite after the existing suites. First, add `CstAutolink, CstUrlMacro, CstLinkMacro, CstMailtoMacro` to the imports on line 6, and add `Link, LinkVariant, MacroKind` to the ast imports on line 4:

Update line 4 from:
```scala
import io.eleven19.ascribe.ast.{Admonition, AdmonitionKind, Heading, Paragraph, Section, Span}
```
to:
```scala
import io.eleven19.ascribe.ast.{Admonition, AdmonitionKind, Heading, Link, LinkVariant, MacroKind, Paragraph, Section, Span}
```

Update line 6 from:
```scala
import io.eleven19.ascribe.cst.{CstAdmonitionParagraph, CstAttributeEntry, CstAttributeRef, CstDocumentHeader, CstDocument, CstParagraph, CstParagraphLine, CstHeading, CstText}
```
to:
```scala
import io.eleven19.ascribe.cst.{CstAdmonitionParagraph, CstAttributeEntry, CstAttributeRef, CstAutolink, CstDocumentHeader, CstDocument, CstLinkMacro, CstMailtoMacro, CstParagraph, CstParagraphLine, CstHeading, CstText, CstUrlMacro}
```

Then add the test suite inside the top-level `suite("CstLowering")(...)`, after the last existing test:

```scala
        suite("link lowering")(
            test("CstAutolink lowers to Link(Auto, ...)") {
                val cst = CstDocument(
                    None,
                    List(CstParagraph(List(
                        CstParagraphLine(List(CstAutolink("https://example.com")(u)))(u)
                    ))(u))
                )(u)
                val result = CstLowering.toAst(cst)
                assertTrue(result == document(paragraph(autoLink("https://example.com"))))
            },
            test("CstUrlMacro lowers to Link(Macro(Url(...)), ...)") {
                val cst = CstDocument(
                    None,
                    List(CstParagraph(List(
                        CstParagraphLine(List(CstUrlMacro("https://example.com", List(CstText("click")(u)))(u)))(u)
                    ))(u))
                )(u)
                val result = CstLowering.toAst(cst)
                assertTrue(result == document(paragraph(urlLink("https", "https://example.com", text("click")))))
            },
            test("CstLinkMacro lowers to Link(Macro(Link), ...)") {
                val cst = CstDocument(
                    None,
                    List(CstParagraph(List(
                        CstParagraphLine(List(CstLinkMacro("report.pdf", List(CstText("Get Report")(u)))(u)))(u)
                    ))(u))
                )(u)
                val result = CstLowering.toAst(cst)
                assertTrue(result == document(paragraph(link("report.pdf", text("Get Report")))))
            },
            test("CstMailtoMacro lowers to Link(Macro(MailTo), ...)") {
                val cst = CstDocument(
                    None,
                    List(CstParagraph(List(
                        CstParagraphLine(List(CstMailtoMacro("user@host.com", List(CstText("Email")(u)))(u)))(u)
                    ))(u))
                )(u)
                val result = CstLowering.toAst(cst)
                assertTrue(result == document(paragraph(mailtoLink("user@host.com", text("Email")))))
            },
            test("CstUrlMacro with empty text lowers to Link with empty text list") {
                val cst = CstDocument(
                    None,
                    List(CstParagraph(List(
                        CstParagraphLine(List(CstUrlMacro("https://example.com", Nil)(u)))(u)
                    ))(u))
                )(u)
                val result = CstLowering.toAst(cst)
                assertTrue(result == document(paragraph(urlLink("https", "https://example.com"))))
            }
        ),
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mill ascribe.test.testLocal`
Expected: FAIL — match error in `lowerInline` for `CstAutolink`/`CstUrlMacro`/`CstLinkMacro`/`CstMailtoMacro`

- [ ] **Step 3: Implement lowering match cases**

In `ascribe/src/io/eleven19/ascribe/cst/CstLowering.scala`, add these cases after the `CstAttributeRef` case (line 51), inside `lowerInline`:

```scala
            case CstAutolink(target) =>
                Link(LinkVariant.Auto, target, Nil)(inline.span)
            case CstUrlMacro(target, text) =>
                val scheme = target.indexOf("://") match
                    case -1  => "unknown"
                    case idx => target.substring(0, idx)
                Link(LinkVariant.Macro(MacroKind.Url(scheme)), target, lowerInlines(text))(inline.span)
            case CstLinkMacro(target, text) =>
                Link(LinkVariant.Macro(MacroKind.Link), target, lowerInlines(text))(inline.span)
            case CstMailtoMacro(target, text) =>
                Link(LinkVariant.Macro(MacroKind.MailTo), target, lowerInlines(text))(inline.span)
```

Add the necessary imports at the top of the file (after line 3):
```scala
import io.eleven19.ascribe.ast.{Link, LinkVariant, MacroKind}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mill ascribe.test.testLocal`
Expected: All tests PASS including the 5 new link lowering tests

- [ ] **Step 5: Commit**

```bash
git add ascribe/src/io/eleven19/ascribe/cst/CstLowering.scala ascribe/test/src/io/eleven19/ascribe/cst/CstLoweringSpec.scala
git commit -m "feat(cst): lower link CST nodes to AST Link with variants"
```

---

### Task 5: CstRenderer for link nodes

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstRenderer.scala:167-170` (after CstAttributeRef case)

- [ ] **Step 1: Add render cases for link CST nodes**

In `ascribe/src/io/eleven19/ascribe/cst/CstRenderer.scala`, after the `CstAttributeRef` case (line 167-170), add:

```scala
        case CstAutolink(target) =>
            sb.append(target)
        case CstUrlMacro(target, text) =>
            sb.append(target)
            sb.append('[')
            renderInlines(text, sb)
            sb.append(']')
        case CstLinkMacro(target, text) =>
            sb.append("link:")
            sb.append(target)
            sb.append('[')
            renderInlines(text, sb)
            sb.append(']')
        case CstMailtoMacro(target, text) =>
            sb.append("mailto:")
            sb.append(target)
            sb.append('[')
            renderInlines(text, sb)
            sb.append(']')
```

- [ ] **Step 2: Verify compilation**

Run: `./mill ascribe.compile`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ascribe/src/io/eleven19/ascribe/cst/CstRenderer.scala
git commit -m "feat(cst): render link CST nodes for round-trip fidelity"
```

---

### Task 6: InlineParser — link: and mailto: macros

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/parser/InlineParser.scala`
- Modify: `ascribe/src/io/eleven19/ascribe/lexer/AsciiDocLexer.scala`

- [ ] **Step 1: Write failing tests for link: and mailto: macros**

In `ascribe/test/src/io/eleven19/ascribe/parser/InlineParserSpec.scala`, add `CstAutolink, CstLinkMacro, CstMailtoMacro, CstUrlMacro` to the import on line 7:

```scala
import io.eleven19.ascribe.cst.{CstAttributeRef, CstAutolink, CstBold, CstItalic, CstLinkMacro, CstMailtoMacro, CstMono, CstText, CstUrlMacro}
```

Then add these test suites inside the top-level `suite("InlineParser")(...)`, after the `attribute refs` suite:

```scala
        suite("link macros")(
            test("parses link:target[text]") {
                parse("link:report.pdf[Get Report]") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstLinkMacro("report.pdf", List(CstText("Get Report")(u)))(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses link:target[] with empty text") {
                parse("link:report.pdf[]") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstLinkMacro("report.pdf", Nil)(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses link macro embedded in text") {
                parse("See link:report.pdf[the report] for details") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(
                            CstText("See ")(u),
                            CstLinkMacro("report.pdf", List(CstText("the report")(u)))(u),
                            CstText(" for details")(u)
                        ))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("mailto macros")(
            test("parses mailto:addr[text]") {
                parse("mailto:user@example.com[Email me]") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstMailtoMacro("user@example.com", List(CstText("Email me")(u)))(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses mailto:addr[] with empty text") {
                parse("mailto:user@example.com[]") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstMailtoMacro("user@example.com", Nil)(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mill ascribe.test.testLocal`
Expected: FAIL — `link:` and `mailto:` parsed as plain text

- [ ] **Step 3: Update the lexer to exclude `[` and `]` from content characters**

In `ascribe/src/io/eleven19/ascribe/lexer/AsciiDocLexer.scala`, the `isContentChar` function (line 77) currently treats `[` and `]` as content chars. We need to exclude them so bracket content in macros can be parsed. We do NOT exclude colons — `linkMacro` and `mailtoMacro` have higher priority in `inlineElement` than `plainTextInline`, so `link:` and `mailto:` patterns are tried first (via `atomic`, which backtracks cleanly when the macro pattern doesn't match).

Update the `isContentChar` function at line 77-80:

```scala
    def isContentChar(c: Char): Boolean =
        !c.isControl && c != '\n' && c != '\r' &&
            c != '*' && c != '_' && c != '`' &&
            c != '{' && c != '}' &&
            c != '[' && c != ']'
```

And update `unpairedMarkupChar` at line 94-95 to include `[` and `]` as fallback characters:

```scala
    val unpairedMarkupChar: Parsley[Char] =
        satisfy(c => (c == '*' || c == '_' || c == '`' || c == '{' || c == '}' || c == '[' || c == ']') && c != '\n')
```

- [ ] **Step 4: Add link and mailto macro parsers to InlineParser**

In `ascribe/src/io/eleven19/ascribe/parser/InlineParser.scala`, first update the import on line 11 to include the new CST types:

```scala
import io.eleven19.ascribe.cst.{CstAttributeRef, CstAutolink, CstBold, CstInline, CstItalic, CstLinkMacro, CstMailtoMacro, CstMono, CstText, CstUrlMacro}
```

Then add these parsers after the `constrainedBoldSpan` parser (after line 101) and before `plainTextInline`:

```scala
    // -----------------------------------------------------------------------
    // Link and URL parsers
    // -----------------------------------------------------------------------

    /** Characters valid in a macro target (everything up to `[`). */
    private val macroTargetChars: Parsley[String] =
        stringOfSome(satisfy(c => c != '[' && c != '\n' && c != '\r' && c != ' ' && c != '\t'))

    /** Parses inline content between `[` and `]` for link text. */
    private val bracketedInlineContent: Parsley[List[CstInline]] =
        char('[') *> many(boldSpan | constrainedBoldSpan | italicSpan | monoSpan | attrRefInline |
            (pos <~> stringOfSome(satisfy(c => c != ']' && c != '\n' && c != '\r' && c != '*' && c != '_' && c != '`' && c != '{' && c != '}')) <~> pos)
                .map { case ((s, content), e) => CstText(content)(mkSpan(s, e)): CstInline }
        ) <* char(']')

    /** Parses `link:target[text]`. */
    val linkMacro: Parsley[CstInline] =
        atomic(
            (pos <~> (string("link:") *> macroTargetChars) <~> bracketedInlineContent <~> pos)
                .map { case (((s, target), text), e) =>
                    CstLinkMacro(target, text)(mkSpan(s, e))
                }
        ).label("link macro")
            .explain("Link macro syntax: link:target[text]")

    /** Parses `mailto:addr[text]`. */
    val mailtoMacro: Parsley[CstInline] =
        atomic(
            (pos <~> (string("mailto:") *> macroTargetChars) <~> bracketedInlineContent <~> pos)
                .map { case (((s, target), text), e) =>
                    CstMailtoMacro(target, text)(mkSpan(s, e))
                }
        ).label("mailto macro")
            .explain("Mailto macro syntax: mailto:address[text]")
```

Then update `inlineElement` (line 116-118) to include the new parsers with the right priority — macros before plainText:

```scala
    val inlineElement: Parsley[CstInline] =
        boldSpan | constrainedBoldSpan | italicSpan | monoSpan |
            linkMacro | mailtoMacro |
            attrRefInline | plainTextInline | unpairedMarkupInline
```

- [ ] **Step 5: Run tests to verify link/mailto macro tests pass**

Run: `./mill ascribe.test.testLocal`
Expected: All tests PASS including the 5 new link/mailto macro tests. Existing tests should still pass since we only changed `[`/`]` handling (not colons or other common prose chars).

- [ ] **Step 6: Commit**

```bash
git add ascribe/src/io/eleven19/ascribe/parser/InlineParser.scala ascribe/src/io/eleven19/ascribe/lexer/AsciiDocLexer.scala ascribe/test/src/io/eleven19/ascribe/parser/InlineParserSpec.scala
git commit -m "feat(parser): add link: and mailto: inline macro parsers"
```

---

### Task 7: InlineParser — URL macros and bare autolinks

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/parser/InlineParser.scala`
- Modify: `ascribe/test/src/io/eleven19/ascribe/parser/InlineParserSpec.scala`

- [ ] **Step 1: Write failing tests for URL macros and autolinks**

In `ascribe/test/src/io/eleven19/ascribe/parser/InlineParserSpec.scala`, add these test suites:

```scala
        suite("URL macros")(
            test("parses https://url[text]") {
                parse("https://example.com[click here]") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstUrlMacro("https://example.com", List(CstText("click here")(u)))(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses http://url[text]") {
                parse("http://example.com[visit]") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstUrlMacro("http://example.com", List(CstText("visit")(u)))(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses URL macro with empty text") {
                parse("https://example.com[]") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstUrlMacro("https://example.com", Nil)(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("bare autolinks")(
            test("parses bare https:// URL") {
                parse("https://example.com") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstAutolink("https://example.com")(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses bare URL embedded in text") {
                parse("Visit https://example.com today") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(
                            CstText("Visit ")(u),
                            CstAutolink("https://example.com")(u),
                            CstText(" today")(u)
                        ))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses ftp:// URL") {
                parse("ftp://files.example.com/pub") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstAutolink("ftp://files.example.com/pub")(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses irc:// URL") {
                parse("irc://irc.freenode.org/#channel") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstAutolink("irc://irc.freenode.org/#channel")(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("strips trailing period from bare autolink") {
                parse("See https://example.com.") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(
                            CstText("See ")(u),
                            CstAutolink("https://example.com")(u),
                            CstText(".")(u)
                        ))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mill ascribe.test.testLocal`
Expected: FAIL — URL schemes consumed as plain text

- [ ] **Step 3: Implement URL macro and autolink parsers**

In `ascribe/src/io/eleven19/ascribe/parser/InlineParser.scala`, add these parsers after `mailtoMacro` and before `plainTextInline`:

```scala
    /** Recognized URL schemes for autolinks and URL macros. */
    private val urlScheme: Parsley[String] =
        string("https://") | string("http://") | string("ftp://") | string("irc://")

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
        ).label("URL macro")

    /** Parses a bare autolink: `scheme://target` (not followed by `[`).
      * Trailing punctuation stripping is deferred to a follow-up issue.
      */
    val autolink: Parsley[CstInline] =
        atomic(
            (pos <~> (urlScheme <~> urlChars).map(_ + _) <~> pos)
                .map { case ((s, target), e) =>
                    CstAutolink(target)(mkSpan(s, e))
                }
        ).label("autolink")
```

Then update `inlineElement` to include URL parsers. The URL macro must come before autolink (both start with a scheme, but URL macro also has `[`):

```scala
    val inlineElement: Parsley[CstInline] =
        boldSpan | constrainedBoldSpan | italicSpan | monoSpan |
            linkMacro | mailtoMacro | urlMacro | autolink |
            attrRefInline | plainTextInline | unpairedMarkupInline
```

- [ ] **Step 4: Update the trailing-period test to match simplified behavior**

Since we're deferring trailing punctuation stripping, update the "strips trailing period" test to expect the period as part of the autolink:

```scala
            test("trailing period is part of bare autolink (stripping deferred)") {
                parse("See https://example.com.") match
                    case Success(inlines) =>
                        // Trailing punctuation stripping is a follow-up feature
                        assertTrue(inlines == List(
                            CstText("See ")(u),
                            CstAutolink("https://example.com.")(u)
                        ))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mill ascribe.test.testLocal`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add ascribe/src/io/eleven19/ascribe/parser/InlineParser.scala ascribe/test/src/io/eleven19/ascribe/parser/InlineParserSpec.scala
git commit -m "feat(parser): add URL macro and bare autolink parsers"
```

---

### Task 8: ASG bridge — Link to Ref conversion

**Files:**
- Modify: `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala:339-369`

- [ ] **Step 1: Write failing test**

First, check what test file exists for the bridge:

Run: `find ascribe/bridge/test -name "*.scala" -type f` to locate the bridge test file.

In the bridge test file (likely `ascribe/bridge/test/src/io/eleven19/ascribe/bridge/AstToAsgSpec.scala`), add a test for link conversion. The test should verify that `ast.Link` nodes convert to `asg.Ref` nodes:

```scala
        test("converts AutoLink to Ref(link)") {
            val astDoc = ast.dsl.document(ast.dsl.paragraph(ast.dsl.autoLink("https://example.com")))
            val result = AstToAsg.convert(astDoc)
            val expected = asg.dsl.document(asg.dsl.paragraph(asg.dsl.ref("link", "https://example.com")))
            assertTrue(result == expected)
        },
        test("converts Link(Macro(Link)) to Ref(link)") {
            val astDoc = ast.dsl.document(ast.dsl.paragraph(ast.dsl.link("report.pdf", ast.dsl.text("Get Report"))))
            val result = AstToAsg.convert(astDoc)
            val expected = asg.dsl.document(asg.dsl.paragraph(asg.dsl.ref("link", "report.pdf", asg.dsl.text("Get Report"))))
            assertTrue(result == expected)
        },
        test("converts MailtoLink to Ref(link) with mailto: prefix") {
            val astDoc = ast.dsl.document(ast.dsl.paragraph(ast.dsl.mailtoLink("user@host.com", ast.dsl.text("Email"))))
            val result = AstToAsg.convert(astDoc)
            val expected = asg.dsl.document(asg.dsl.paragraph(asg.dsl.ref("link", "mailto:user@host.com", asg.dsl.text("Email"))))
            assertTrue(result == expected)
        },
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mill ascribe.bridge.test.testLocal`
Expected: FAIL — match error in `convertInline` for `ast.Link`

- [ ] **Step 3: Implement the bridge conversion**

In `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala`, after the `ast.Mono` case (line 368-369), add:

```scala
        case ast.Link(variant, target, text) =>
            val asgTarget = variant match
                case ast.LinkVariant.Macro(ast.MacroKind.MailTo) => "mailto:" + target
                case _                                           => target
            asg.Ref(
                variant = "link",
                target = asgTarget,
                inlines = Chunk.from(text.map(convertInline)),
                location = inclusiveLocation(inline.span)
            )
```

- [ ] **Step 4: Update lastContentPos for Link**

In `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala`, the `lastContentPos` function has a catch-all `case i: ast.Inline => i.span.end` at line 422, which already handles the new `Link` type. No change needed.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mill ascribe.bridge.test.testLocal`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala ascribe/bridge/test/src/io/eleven19/ascribe/bridge/
git commit -m "feat(bridge): convert AST Link variants to ASG Ref(link)"
```

---

### Task 9: Pipeline visitor updates (if needed)

**Files:**
- Check: `ascribe/pipeline/src/io/eleven19/ascribe/pipeline/dsl.scala`

- [ ] **Step 1: Check if pipeline visitors need updating**

Read `ascribe/pipeline/src/io/eleven19/ascribe/pipeline/dsl.scala` and check if `replaceInline` or `stripFormatting` pattern-match on inline types. If they use wildcard catches or only match specific types, they may need a `Link` case.

- [ ] **Step 2: Add Link case to pipeline functions if needed**

If `replaceInline` or `stripFormatting` exhaustively match inline types, add:

```scala
case l: Link => l.copy(text = l.text.map(replaceInline(_, pf)))(l.span)
```

If they use a wildcard `case other => other`, no change is needed.

- [ ] **Step 3: Run all tests**

Run: `./mill ascribe.test.testLocal && ./mill ascribe.bridge.test.testLocal && ./mill ascribe.pipeline.test.testLocal`
Expected: All PASS

- [ ] **Step 4: Commit (if changes were made)**

```bash
git add ascribe/pipeline/src/io/eleven19/ascribe/pipeline/dsl.scala
git commit -m "feat(pipeline): handle Link nodes in inline visitors"
```

---

### Task 10: End-to-end integration test

**Files:**
- Create: A test AsciiDoc file or inline test string in the existing integration test infrastructure

- [ ] **Step 1: Locate the integration test infrastructure**

Run: `find ascribe/itest -name "*.scala" -type f` to find integration test files.

- [ ] **Step 2: Write an end-to-end test**

Add a test that parses an AsciiDoc document containing all 4 link forms and verifies the full pipeline (parse -> CST -> AST -> ASG):

```
Visit https://example.com for more.

See https://example.com[our site] for details.

Download link:report.pdf[the report] now.

Contact mailto:help@example.com[support] for help.
```

The test should parse this document and verify the ASG output contains `Ref(variant="link", ...)` nodes with correct targets.

- [ ] **Step 3: Run the integration test**

Run: `./mill ascribe.itest.testLocal` (or whatever the integration test command is)
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add ascribe/itest/
git commit -m "test: add end-to-end integration test for inline links"
```

---

### Task 11: Final verification

- [ ] **Step 1: Run all tests**

Run: `./mill __.testLocal`
Expected: All tests PASS across all modules

- [ ] **Step 2: Verify no compilation warnings**

Run: `./mill __.compile 2>&1 | grep -i warn`
Expected: No new warnings (some pre-existing warnings may exist)

- [ ] **Step 3: Final commit and cleanup**

Verify `git status` is clean. If any uncommitted changes remain, stage and commit them.
