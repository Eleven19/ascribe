# Constrained Italic and Monospace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add constrained italic (`_text_`) and monospace (`` `text` ``) with spec-compliant word-boundary enforcement, and retrofit constrained bold.

**Architecture:** Add `constrained: Boolean` to CstItalic/CstMono (matching CstBold pattern), add ConstrainedItalic/ConstrainedMono AST nodes, add Parsley Ref-based word-boundary checking to all constrained parsers, wire through lowering/bridge/renderer/visitor/DSL/pipeline.

**Tech Stack:** Scala 3, Parsley parser combinators (Ref for state), ZIO Test, Mill build

**Spec:** `docs/superpowers/specs/2026-03-27-constrained-italic-mono-design.md`

**Test command:** `./mill ascribe.test.testLocal`

**Bridge test command:** `./mill ascribe.bridge.test.testLocal`

**All modules:** `./mill __.testLocal`

**Working directory:** `/home/damian/code/repos/github/Eleven19/ascribe/.worktrees/feat-constrained-inline`

---

### Task 1: Add `constrained: Boolean` to CstItalic and CstMono

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstNodes.scala:162-176`

- [ ] **Step 1: Update CstItalic to add constrained boolean**

In `ascribe/src/io/eleven19/ascribe/cst/CstNodes.scala`, replace lines 162-168:

```scala
// TODO: Add `constrained: Boolean` when constrained italic (`_text_`) is added
// to the parser. Currently only unconstrained italic (`__text__`) is supported.
// See spec Known Limitations.
case class CstItalic(
    content: List[CstInline]
)(val span: Span)
    extends CstInline derives CanEqual
```

with:

```scala
case class CstItalic(
    content: List[CstInline],
    constrained: Boolean
)(val span: Span)
    extends CstInline derives CanEqual
```

- [ ] **Step 2: Update CstMono to add constrained boolean**

In the same file, replace lines 170-176:

```scala
// TODO: Add `constrained: Boolean` when constrained monospace (`` `text` ``) is
// added to the parser. Currently only unconstrained mono (` ``text`` `) is
// supported. See spec Known Limitations.
case class CstMono(
    content: List[CstInline]
)(val span: Span)
    extends CstInline derives CanEqual
```

with:

```scala
case class CstMono(
    content: List[CstInline],
    constrained: Boolean
)(val span: Span)
    extends CstInline derives CanEqual
```

- [ ] **Step 3: Fix all existing call sites**

The compiler will report errors everywhere `CstItalic(content)` and `CstMono(content)` are constructed without the boolean. Add `constrained = false` to all existing sites:

**InlineParser.scala line 75** — unconstrained italic:
```scala
CstItalic(List(CstText(content)(span)), constrained = false)(span)
```

**InlineParser.scala line 85** — unconstrained mono:
```scala
CstMono(List(CstText(content)(span)), constrained = false)(span)
```

**CstLowering.scala line 49** — update match pattern:
```scala
case CstItalic(content, _) => Italic(lowerInlines(content))(inline.span)
```

**CstLowering.scala line 50** — update match pattern:
```scala
case CstMono(content, _) => Mono(lowerInlines(content))(inline.span)
```

**CstRenderer.scala line 165** — update match pattern:
```scala
case CstItalic(content, false) =>
```

**CstRenderer.scala line 169** — update match pattern:
```scala
case CstMono(content, false) =>
```

**CstVisitor.scala children (line 123-124)** — update match patterns:
```scala
case i: CstItalic => i.content
case m: CstMono   => m.content
```
(These match on type, not fields, so no change needed.)

Also fix any test files that construct CstItalic/CstMono directly (InlineParserSpec.scala, CstLoweringSpec.scala, CstRendererSpec.scala, CstVisitorSpec.scala). Add `constrained = false` or just `false` to match existing unconstrained usage. Search for `CstItalic(` and `CstMono(` in test files and update each.

- [ ] **Step 4: Verify compilation**

Run: `./mill ascribe.compile 2>&1 | tail -5`
Expected: SUCCESS (or warnings about non-exhaustive matches which we'll fix in later tasks)

- [ ] **Step 5: Run tests to verify existing behavior preserved**

Run: `./mill ascribe.test.testLocal 2>&1 | tail -5`
Expected: All existing tests PASS

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(cst): add constrained boolean to CstItalic and CstMono"
```

---

### Task 2: Add ConstrainedItalic and ConstrainedMono AST nodes

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/ast/Document.scala:32` (after ConstrainedBold)

- [ ] **Step 1: Add ConstrainedItalic case class**

In `ascribe/src/io/eleven19/ascribe/ast/Document.scala`, after the `ConstrainedBold` line (line 32), add:

```scala
/** Constrained italic span, surrounded by single underscores: _italic_. */
case class ConstrainedItalic(content: List[Inline])(val span: Span) extends Inline derives CanEqual

/** Constrained monospace span, surrounded by single backticks: `mono`. */
case class ConstrainedMono(content: List[Inline])(val span: Span) extends Inline derives CanEqual
```

- [ ] **Step 2: Add companion objects**

After the existing companion objects (after `ConstrainedBold` companion at line 47), add:

```scala
object ConstrainedItalic extends PosParserBridge1[List[Inline], ConstrainedItalic]:
    def apply(content: List[Inline])(span: Span): ConstrainedItalic = new ConstrainedItalic(content)(span)

object ConstrainedMono extends PosParserBridge1[List[Inline], ConstrainedMono]:
    def apply(content: List[Inline])(span: Span): ConstrainedMono = new ConstrainedMono(content)(span)
```

- [ ] **Step 3: Verify compilation**

Run: `./mill ascribe.compile 2>&1 | tail -5`
Expected: SUCCESS (may show non-exhaustive match warnings — expected, fixed in later tasks)

- [ ] **Step 4: Commit**

```bash
git add ascribe/src/io/eleven19/ascribe/ast/Document.scala
git commit -m "feat(ast): add ConstrainedItalic and ConstrainedMono case classes"
```

---

### Task 3: DSL, visitor, and pipeline updates for new AST types

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/ast/dsl.scala:29`
- Modify: `ascribe/src/io/eleven19/ascribe/ast/AstVisitor.scala`
- Modify: `ascribe/pipeline/src/io/eleven19/ascribe/pipeline/dsl.scala`
- Modify: `ascribe/pipeline/src/io/eleven19/ascribe/pipeline/AsciiDocRenderer.scala`
- Modify: `ascribe/itest/src/io/eleven19/ascribe/AsciiDocParserSteps.scala`

- [ ] **Step 1: Add DSL constructors**

In `ascribe/src/io/eleven19/ascribe/ast/dsl.scala`, after the `mono` definition (line 29), add:

```scala
    def constrainedItalic(inlines: Inline*): ConstrainedItalic = ConstrainedItalic(inlines.toList)(u)
    def constrainedMono(inlines: Inline*): ConstrainedMono     = ConstrainedMono(inlines.toList)(u)
```

- [ ] **Step 2: Update AstVisitor trait**

In `ascribe/src/io/eleven19/ascribe/ast/AstVisitor.scala`, add visitor methods after `visitMono` (line 50):

```scala
    def visitConstrainedItalic(node: ConstrainedItalic): A = visitInline(node)
    def visitConstrainedMono(node: ConstrainedMono): A     = visitInline(node)
```

In the `visit` dispatch method, add cases after `case n: Mono` (line 87):

```scala
        case n: ConstrainedItalic => visitor.visitConstrainedItalic(n)
        case n: ConstrainedMono   => visitor.visitConstrainedMono(n)
```

In the `children` method, add cases after `case m: Mono` (line 121):

```scala
        case ci: ConstrainedItalic => ci.content
        case cm: ConstrainedMono   => cm.content
```

- [ ] **Step 3: Update pipeline stripFormatting and flattenInlines**

In `ascribe/pipeline/src/io/eleven19/ascribe/pipeline/dsl.scala`, add cases to `stripFormatting` (after line 38):

```scala
            case ConstrainedItalic(content) => RewriteAction.Replace(Text(flattenInlines(content))(Span.unknown))
            case ConstrainedMono(content)   => RewriteAction.Replace(Text(flattenInlines(content))(Span.unknown))
```

Add cases to `flattenInlines` (after line 47):

```scala
            case ConstrainedItalic(content) => flattenInlines(content)
            case ConstrainedMono(content)   => flattenInlines(content)
```

- [ ] **Step 4: Update AsciiDocRenderer**

In `ascribe/pipeline/src/io/eleven19/ascribe/pipeline/AsciiDocRenderer.scala`, add cases after `Mono` (line 160):

```scala
        case ConstrainedItalic(content)    => s"_${renderInlines(content)}_"
        case ConstrainedMono(content)      => s"`${renderInlines(content)}`"
```

- [ ] **Step 5: Update itest inlinesToText**

In `ascribe/itest/src/io/eleven19/ascribe/AsciiDocParserSteps.scala`, add cases after `Mono` (line 176):

```scala
            case ConstrainedItalic(cs)   => inlinesToText(cs)
            case ConstrainedMono(cs)     => inlinesToText(cs)
```

- [ ] **Step 6: Verify full compilation**

Run: `./mill __.compile 2>&1 | grep -E "FAILED|SUCCESS" | tail -5`
Expected: All SUCCESS

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(ast): wire ConstrainedItalic/Mono through DSL, visitor, pipeline, renderer"
```

---

### Task 4: CstLowering and CstRenderer for constrained variants

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstLowering.scala:49-50`
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstRenderer.scala:165-172`

- [ ] **Step 1: Write failing lowering tests**

In `ascribe/test/src/io/eleven19/ascribe/cst/CstLoweringSpec.scala`, add imports for the new types and CST types at the top, then add a test suite:

```scala
        suite("constrained italic/mono lowering")(
            test("CstItalic(constrained=false) lowers to Italic") {
                val cst = CstDocument(
                    None,
                    List(CstParagraph(List(
                        CstParagraphLine(List(CstItalic(List(CstText("em")(u)), constrained = false)(u)))(u)
                    ))(u))
                )(u)
                assertTrue(CstLowering.toAst(cst) == document(paragraph(italic(text("em")))))
            },
            test("CstItalic(constrained=true) lowers to ConstrainedItalic") {
                val cst = CstDocument(
                    None,
                    List(CstParagraph(List(
                        CstParagraphLine(List(CstItalic(List(CstText("em")(u)), constrained = true)(u)))(u)
                    ))(u))
                )(u)
                assertTrue(CstLowering.toAst(cst) == document(paragraph(constrainedItalic(text("em")))))
            },
            test("CstMono(constrained=false) lowers to Mono") {
                val cst = CstDocument(
                    None,
                    List(CstParagraph(List(
                        CstParagraphLine(List(CstMono(List(CstText("cd")(u)), constrained = false)(u)))(u)
                    ))(u))
                )(u)
                assertTrue(CstLowering.toAst(cst) == document(paragraph(mono(text("cd")))))
            },
            test("CstMono(constrained=true) lowers to ConstrainedMono") {
                val cst = CstDocument(
                    None,
                    List(CstParagraph(List(
                        CstParagraphLine(List(CstMono(List(CstText("cd")(u)), constrained = true)(u)))(u)
                    ))(u))
                )(u)
                assertTrue(CstLowering.toAst(cst) == document(paragraph(constrainedMono(text("cd")))))
            }
        ),
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mill ascribe.test.testLocal 2>&1 | tail -5`
Expected: FAIL — lowering doesn't dispatch constrained italic/mono to new AST types yet

- [ ] **Step 3: Update CstLowering match cases**

In `ascribe/src/io/eleven19/ascribe/cst/CstLowering.scala`, replace the italic and mono cases (lines 49-50):

```scala
            case CstItalic(content, false) => Italic(lowerInlines(content))(inline.span)
            case CstItalic(content, true)  => ConstrainedItalic(lowerInlines(content))(inline.span)
            case CstMono(content, false)   => Mono(lowerInlines(content))(inline.span)
            case CstMono(content, true)    => ConstrainedMono(lowerInlines(content))(inline.span)
```

- [ ] **Step 4: Update CstRenderer match cases**

In `ascribe/src/io/eleven19/ascribe/cst/CstRenderer.scala`, replace the italic case (lines 165-168):

```scala
        case CstItalic(content, false) =>
            sb.append("__")
            renderInlines(content, sb)
            sb.append("__")
        case CstItalic(content, true) =>
            sb.append("_")
            renderInlines(content, sb)
            sb.append("_")
```

Replace the mono case (lines 169-172):

```scala
        case CstMono(content, false) =>
            sb.append("``")
            renderInlines(content, sb)
            sb.append("``")
        case CstMono(content, true) =>
            sb.append("`")
            renderInlines(content, sb)
            sb.append("`")
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mill ascribe.test.testLocal 2>&1 | tail -5`
Expected: All PASS

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(cst): lower and render constrained italic/mono variants"
```

---

### Task 5: ASG bridge for constrained italic and mono

**Files:**
- Modify: `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala:356-369`

- [ ] **Step 1: Add bridge conversion cases**

In `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala`, after the `ast.Mono` case (line 369), add:

```scala
        case ast.ConstrainedItalic(content) =>
            asg.Span(
                variant = "emphasis",
                form = "constrained",
                inlines = Chunk.from(content.map(convertInline)),
                location = inclusiveLocation(inline.span)
            )
        case ast.ConstrainedMono(content) =>
            asg.Span(
                variant = "code",
                form = "constrained",
                inlines = Chunk.from(content.map(convertInline)),
                location = inclusiveLocation(inline.span)
            )
```

- [ ] **Step 2: Verify compilation and run bridge tests**

Run: `./mill ascribe.bridge.test.testLocal 2>&1 | tail -5`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala
git commit -m "feat(bridge): convert ConstrainedItalic/Mono to ASG Span with form=constrained"
```

---

### Task 6: Parser — constrained italic, mono, and word-boundary enforcement

This is the core task. Add Ref-based boundary tracking and the three constrained parsers.

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/parser/InlineParser.scala`

- [ ] **Step 1: Write failing parser tests**

In `ascribe/test/src/io/eleven19/ascribe/parser/InlineParserSpec.scala`, add test suites:

```scala
        suite("constrained italic")(
            test("parses _italic_ at start of line") {
                parse("_italic_") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstItalic(List(CstText("italic")(u)), true)(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses _italic_ embedded in text") {
                parse("hello _world_ end") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(
                            CstText("hello ")(u),
                            CstItalic(List(CstText("world")(u)), true)(u),
                            CstText(" end")(u)
                        ))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("does not parse _italic_ mid-word") {
                parse("foo_bar_baz") match
                    case Success(inlines) =>
                        // Should NOT produce CstItalic — underscores are mid-word
                        assertTrue(!inlines.exists { case _: CstItalic => true; case _ => false })
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses _italic_ after punctuation") {
                parse("(_italic_)") match
                    case Success(inlines) =>
                        assertTrue(inlines.exists { case _: CstItalic => true; case _ => false })
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("constrained monospace")(
            test("parses `code` at start of line") {
                parse("`code`") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstMono(List(CstText("code")(u)), true)(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses `code` embedded in text") {
                parse("use `cmd` now") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(
                            CstText("use ")(u),
                            CstMono(List(CstText("cmd")(u)), true)(u),
                            CstText(" now")(u)
                        ))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("does not parse `code` mid-word") {
                parse("foo`bar`baz") match
                    case Success(inlines) =>
                        assertTrue(!inlines.exists { case _: CstMono => true; case _ => false })
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("constrained bold boundary enforcement")(
            test("*bold* at start of line still works") {
                parse("*bold*") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstBold(List(CstText("bold")(u)), true)(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("does not parse *bold* mid-word") {
                parse("foo*bar*baz") match
                    case Success(inlines) =>
                        assertTrue(!inlines.exists { case CstBold(_, true) => true; case _ => false })
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mill ascribe.test.testLocal 2>&1 | tail -5`
Expected: FAIL — constrained italic/mono parsers don't exist yet, mid-word tests may pass incorrectly

- [ ] **Step 3: Add Ref-based boundary tracking and constrained parsers**

In `ascribe/src/io/eleven19/ascribe/parser/InlineParser.scala`:

**Update imports** (add `Ref`, `lookAhead`, `eof`, `unit`, `empty`):

```scala
import parsley.Parsley
import parsley.Parsley.{atomic, empty, eof, lookAhead, many, notFollowedBy, pure, some, unit}
import parsley.character.{char, satisfy, string, stringOfMany, stringOfSome}
import parsley.combinator.manyTill
import parsley.errors.combinator.ErrorMethods
import parsley.position.pos
import parsley.state.Ref
```

**Add boundary helpers** after the `delimitedContent` method (after line 54):

```scala
    // -----------------------------------------------------------------------
    // Word-boundary tracking for constrained formatting
    // -----------------------------------------------------------------------

    /** Tracks the last consumed character for constrained delimiter boundary checks. */
    private val lastChar: Ref[Option[Char]] = Ref.make[Option[Char]]

    /** True for characters that are part of a word (letters, digits, underscore). */
    private def isWordChar(c: Char): Boolean = c.isLetterOrDigit || c == '_'

    /** True for characters that can follow a constrained closing delimiter. */
    private def isConstrainedCloseChar(c: Char): Boolean =
        c == ' ' || c == '\t' || c == '\n' || c == '\r' ||
            c == ',' || c == ';' || c == '"' || c == '.' || c == '?' || c == '!'

    /** Succeeds only when the last consumed char allows a constrained opening delimiter. */
    private val atConstrainedOpen: Parsley[Unit] =
        lastChar.get.flatMap {
            case None                       => unit
            case Some(c) if !isWordChar(c)  => unit
            case _                          => empty
        }

    /** Succeeds only when the next char allows a constrained closing delimiter. */
    private val atConstrainedClose: Parsley[Unit] =
        lookAhead(satisfy(isConstrainedCloseChar)).void | eof
```

**Add constrained italic and mono parsers** after `constrainedBoldSpan` (after line 112):

```scala
    /** Parses a constrained _italic_ span: `_content_`.
      * Requires word-boundary: non-word char (or SOL) before `_`, space/punct/EOL after closing `_`.
      */
    val constrainedItalicSpan: Parsley[CstInline] =
        atomic(atConstrainedOpen *> (pos <~> delimitedContent("_", "_") <~> pos) <* atConstrainedClose)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                CstItalic(List(CstText(content)(span)), constrained = true)(span)
            }
            .label("constrained italic span")

    /** Parses a constrained `monospace` span: `` `content` ``.
      * Requires word-boundary: non-word char (or SOL) before `` ` ``, space/punct/EOL after closing `` ` ``.
      */
    val constrainedMonoSpan: Parsley[CstInline] =
        atomic(atConstrainedOpen *> (pos <~> delimitedContent("`", "`") <~> pos) <* atConstrainedClose)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                CstMono(List(CstText(content)(span)), constrained = true)(span)
            }
            .label("constrained monospace span")
```

**Retrofit constrainedBoldSpan** (replace lines 106-112):

```scala
    val constrainedBoldSpan: Parsley[CstInline] =
        atomic(atConstrainedOpen *> (pos <~> delimitedContent("*", "*") <~> pos) <* atConstrainedClose)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                CstBold(List(CstText(content)(span)), constrained = true)(span)
            }
            .label("constrained bold span")
```

**Update `plainTextInline`** to track last consumed character (replace lines 193-196):

```scala
    val plainTextInline: Parsley[CstInline] =
        (pos <~> some(notFollowedBy(linkOrUrlPrefix) *> contentChar).map(_.mkString) <~> pos)
            .map { case ((s, content), e) => CstText(content)(mkSpan(s, e)) }
            .flatMap { node =>
                val last = node.content.lastOption
                lastChar.set(last) *> pure(node: CstInline)
            }
            .label("text")
```

**Update `unpairedMarkupInline`** to track last consumed character (replace line 200):

```scala
    val unpairedMarkupInline: Parsley[CstInline] =
        (pos <~> unpairedMarkupChar <~> pos)
            .map { case ((s, c), e) => (CstText(c.toString)(mkSpan(s, e)), c) }
            .flatMap { case (node, c) =>
                lastChar.set(Some(c)) *> pure(node: CstInline)
            }
            .hide
```

**Update `inlineElement`** — add constrained italic and mono in priority order (replace lines 205-208):

```scala
    val inlineElement: Parsley[CstInline] =
        boldSpan | italicSpan | monoSpan |
            constrainedBoldSpan | constrainedItalicSpan | constrainedMonoSpan |
            linkMacro | mailtoMacro | urlMacro | autolink |
            attrRefInline | plainTextInline | unpairedMarkupInline
```

**Update `lineContent`** to initialize the ref (replace line 211):

```scala
    val lineContent: Parsley[List[CstInline]] = lastChar.set(None) *> many(inlineElement)
```

**Update delimited span parsers** (boldSpan, italicSpan, monoSpan, and all constrained spans) to also update `lastChar` after they consume their closing delimiter. The closing delimiter chars are: `*`, `_`, `` ` ``. Add a `lastChar.set(Some(closingChar))` after each. The simplest approach: wrap the map result with a flatMap:

For **boldSpan** (after line 65):
```scala
    val boldSpan: Parsley[CstInline] =
        (pos <~> delimitedContent("**", "**") <~> pos)
            .map { case ((s, content), e) =>
                val span = mkSpan(s, e)
                CstBold(List(CstText(content)(span)), constrained = false)(span)
            }
            .flatMap(node => lastChar.set(Some('*')) *> pure(node: CstInline))
            .label("bold span")
            .explain("Bold text is surrounded by double asterisks, e.g. **bold**")
```

Apply the same pattern to **italicSpan** (set `Some('_')`), **monoSpan** (set `Some('`')`), **constrainedBoldSpan** (set `Some('*')`), **constrainedItalicSpan** (set `Some('_')`), **constrainedMonoSpan** (set `Some('`')`).

Also update **attrRefInline** to set `lastChar.set(Some('}'))` and all link parsers to set `lastChar.set(Some(']'))` for URL/link/mailto macros, and set to the last char of the URL for autolinks.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mill ascribe.test.testLocal 2>&1 | tail -5`
Expected: All PASS including new constrained tests and boundary enforcement tests

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(parser): add constrained italic/mono parsers with word-boundary enforcement

Uses Parsley Ref to track last consumed character. Constrained
delimiters require non-word char (or SOL) before opening and
space/punct/EOL after closing. Retrofits constrained bold."
```

---

### Task 7: Full test suite and final verification

- [ ] **Step 1: Run all module tests**

Run: `./mill __.testLocal 2>&1 | grep -E "FAILED|SUCCESS" | tail -5`
Expected: All SUCCESS, 0 FAILED

- [ ] **Step 2: Run formatting**

Run: `./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources 2>&1 | tail -3`
Expected: SUCCESS

- [ ] **Step 3: Verify tests still pass after formatting**

Run: `./mill __.testLocal 2>&1 | grep -E "FAILED|SUCCESS" | tail -3`
Expected: All SUCCESS

- [ ] **Step 4: Commit formatting if any changes**

```bash
git add -A && git diff --cached --stat && git commit -m "style: apply scalafmt formatting" || echo "nothing to format"
```

- [ ] **Step 5: Verify clean state**

Run: `git status`
Expected: clean working tree
