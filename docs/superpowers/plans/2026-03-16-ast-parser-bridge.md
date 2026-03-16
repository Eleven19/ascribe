# AST Parser Bridge Pattern Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the AST from Scala 3 enums to sealed traits + case classes with Parsley parser bridge pattern for automatic source position tracking.

**Architecture:** Define `Position`/`Span` types, custom `PosParserBridge` traits that wrap Parsley's `pos` combinator, refactor all AST types to use second parameter lists for `Span`, update parsers to use bridge constructors, and update all tests.

**Tech Stack:** Scala 3.8.2, Parsley 4.6.2 (`parsley.position.pos`, `parsley.generic`), ZIO Test, Cucumber

**Spec:** `docs/superpowers/specs/2026-03-16-ast-parser-bridge-design.md`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `ascribe/src/io/github/eleven19/ascribe/ast/Position.scala` | Create | `Position`, `Span`, `Span.unknown` |
| `ascribe/src/io/github/eleven19/ascribe/ast/ParserBridges.scala` | Create | `PosParserBridge0/1/2/3` traits |
| `ascribe/src/io/github/eleven19/ascribe/ast/Document.scala` | Rewrite | Sealed traits + case classes + bridge companions |
| `ascribe/src/io/github/eleven19/ascribe/parser/InlineParser.scala` | Modify | Manual `pos` captures for inline spans |
| `ascribe/src/io/github/eleven19/ascribe/parser/BlockParser.scala` | Modify | Bridge constructors for blocks |
| `ascribe/src/io/github/eleven19/ascribe/parser/DocumentParser.scala` | Modify | Bridge constructor for `Document` |
| `ascribe/src/io/github/eleven19/ascribe/Ascribe.scala` | No change | Public API unchanged |
| `ascribe/test/src/io/github/eleven19/ascribe/TestHelpers.scala` | Create | Convenience constructors with `Span.unknown` |
| `ascribe/test/src/io/github/eleven19/ascribe/AscribeSpec.scala` | Modify | Use `TestHelpers` |
| `ascribe/test/src/io/github/eleven19/ascribe/parser/DocumentParserSpec.scala` | Modify | Use `TestHelpers` |
| `ascribe/test/src/io/github/eleven19/ascribe/parser/InlineParserSpec.scala` | Modify | Use `TestHelpers` |
| `ascribe/itest/src/io/github/eleven19/ascribe/AsciiDocParserSteps.scala` | Modify | Update pattern matches from `Block.X`/`Inline.X` to top-level names |

---

## Chunk 1: Foundation Types

### Task 1: Create Position and Span types

**Files:**
- Create: `ascribe/src/io/github/eleven19/ascribe/ast/Position.scala`

- [ ] **Step 1: Create the Position.scala file**

```scala
package io.github.eleven19.ascribe.ast

/** A source position identified by line and column (both 1-based, as returned by Parsley's `pos`). */
case class Position(line: Int, col: Int)

/** A source span from start to end position. */
case class Span(start: Position, end: Position)

object Span:
  /** Sentinel for tests and contexts where position is irrelevant.
    * Scala case class equals only considers the first parameter list,
    * so nodes created with Span.unknown still compare equal to nodes
    * with real spans.
    */
  val unknown: Span = Span(Position(0, 0), Position(0, 0))
```

- [ ] **Step 2: Verify it compiles**

Run: `./mill ascribe.compile`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ascribe/src/io/github/eleven19/ascribe/ast/Position.scala
git commit -m "feat: add Position and Span types for source tracking"
```

### Task 2: Create PosParserBridge traits

**Files:**
- Create: `ascribe/src/io/github/eleven19/ascribe/ast/ParserBridges.scala`

- [ ] **Step 1: Create the ParserBridges.scala file**

```scala
package io.github.eleven19.ascribe.ast

import parsley.Parsley
import parsley.position.pos

/** Helper to construct a Span from raw (line, col) tuples returned by Parsley's `pos`. */
def mkSpan(s: (Int, Int), e: (Int, Int)): Span =
  Span(Position(s._1, s._2), Position(e._1, e._2))

/** Bridge for zero-argument AST nodes. Captures start and end position. */
trait PosParserBridge0[+A]:
  def apply()(span: Span): A
  def parser: Parsley[A] =
    (pos <~> pos).map { case (s, e) => apply()(mkSpan(s, e)) }

/** Bridge for single-argument AST nodes. Captures start and end position around the argument. */
trait PosParserBridge1[-A, +B]:
  def apply(a: A)(span: Span): B
  def apply(a: Parsley[A]): Parsley[B] =
    (pos <~> a <~> pos).map { case ((s, a), e) => apply(a)(mkSpan(s, e)) }

/** Bridge for two-argument AST nodes. */
trait PosParserBridge2[-A, -B, +C]:
  def apply(a: A, b: B)(span: Span): C
  def apply(a: Parsley[A], b: Parsley[B]): Parsley[C] =
    (pos <~> a <~> b <~> pos).map { case (((s, a), b), e) => apply(a, b)(mkSpan(s, e)) }

/** Bridge for three-argument AST nodes. */
trait PosParserBridge3[-A, -B, -C, +D]:
  def apply(a: A, b: B, c: C)(span: Span): D
  def apply(a: Parsley[A], b: Parsley[B], c: Parsley[C]): Parsley[D] =
    (pos <~> a <~> b <~> c <~> pos).map { case ((((s, a), b), c), e) =>
      apply(a, b, c)(mkSpan(s, e))
    }
```

- [ ] **Step 2: Verify it compiles**

Run: `./mill ascribe.compile`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ascribe/src/io/github/eleven19/ascribe/ast/ParserBridges.scala
git commit -m "feat: add PosParserBridge traits for position-aware AST construction"
```

## Chunk 2: AST Refactoring

### Task 3: Refactor AST from enums to sealed traits + case classes

**Files:**
- Rewrite: `ascribe/src/io/github/eleven19/ascribe/ast/Document.scala`

- [ ] **Step 1: Rewrite Document.scala**

Replace the entire file contents with:

```scala
package io.github.eleven19.ascribe.ast

/** A list of inline elements forming the content of a single line. */
type InlineContent = List[Inline]

/** An inline element within a paragraph, heading, or list item. */
sealed trait Inline:
  def span: Span

/** Plain text content. */
case class Text(content: String)(val span: Span) extends Inline derives CanEqual

/** Bold span, surrounded by double asterisks: **bold**. */
case class Bold(content: List[Inline])(val span: Span) extends Inline derives CanEqual

/** Italic span, surrounded by double underscores: __italic__. */
case class Italic(content: List[Inline])(val span: Span) extends Inline derives CanEqual

/** Monospace span, surrounded by double backticks: ``mono``. */
case class Mono(content: List[Inline])(val span: Span) extends Inline derives CanEqual

object Text extends PosParserBridge1[String, Text]:
  def apply(content: String)(span: Span): Text = new Text(content)(span)

object Bold extends PosParserBridge1[List[Inline], Bold]:
  def apply(content: List[Inline])(span: Span): Bold = new Bold(content)(span)

object Italic extends PosParserBridge1[List[Inline], Italic]:
  def apply(content: List[Inline])(span: Span): Italic = new Italic(content)(span)

object Mono extends PosParserBridge1[List[Inline], Mono]:
  def apply(content: List[Inline])(span: Span): Mono = new Mono(content)(span)

/** A single item in a list block. */
case class ListItem(content: InlineContent)(val span: Span) derives CanEqual

object ListItem extends PosParserBridge1[InlineContent, ListItem]:
  def apply(content: InlineContent)(span: Span): ListItem = new ListItem(content)(span)

/** A block-level element in an AsciiDoc document. */
sealed trait Block:
  def span: Span

/** A section heading.
  * @param level heading level 1-5 corresponding to = through =====
  * @param title the inline content forming the heading text
  */
case class Heading(level: Int, title: InlineContent)(val span: Span) extends Block derives CanEqual

/** A paragraph of one or more lines of inline content. */
case class Paragraph(content: InlineContent)(val span: Span) extends Block derives CanEqual

/** A bullet list (items prefixed with "* "). */
case class UnorderedList(items: List[ListItem])(val span: Span) extends Block derives CanEqual

/** A numbered list (items prefixed with ". "). */
case class OrderedList(items: List[ListItem])(val span: Span) extends Block derives CanEqual

object Heading extends PosParserBridge2[Int, InlineContent, Heading]:
  def apply(level: Int, title: InlineContent)(span: Span): Heading = new Heading(level, title)(span)

object Paragraph extends PosParserBridge1[InlineContent, Paragraph]:
  def apply(content: InlineContent)(span: Span): Paragraph = new Paragraph(content)(span)

object UnorderedList extends PosParserBridge1[List[ListItem], UnorderedList]:
  def apply(items: List[ListItem])(span: Span): UnorderedList = new UnorderedList(items)(span)

object OrderedList extends PosParserBridge1[List[ListItem], OrderedList]:
  def apply(items: List[ListItem])(span: Span): OrderedList = new OrderedList(items)(span)

/** The top-level document containing an ordered sequence of blocks. */
case class Document(blocks: List[Block])(val span: Span) derives CanEqual

object Document extends PosParserBridge1[List[Block], Document]:
  def apply(blocks: List[Block])(span: Span): Document = new Document(blocks)(span)
```

- [ ] **Step 2: Verify it compiles (expect downstream failures)**

Run: `./mill ascribe.compile`
Expected: SUCCESS (the AST module itself should compile; downstream parsers/tests will break)

- [ ] **Step 3: Commit**

```bash
git add ascribe/src/io/github/eleven19/ascribe/ast/Document.scala
git commit -m "refactor: convert AST from enums to sealed traits with position tracking"
```

## Chunk 3: Parser Updates

> **Note:** When rewriting parser files, preserve existing scaladoc comments from the current code. The examples below show the structural changes but omit scaladoc for brevity. Copy scaladoc from the current files when implementing.

### Task 4: Update InlineParser to use manual position captures

**Files:**
- Modify: `ascribe/src/io/github/eleven19/ascribe/parser/InlineParser.scala`

- [ ] **Step 1: Rewrite InlineParser.scala**

Replace the entire file contents. Key changes: import `pos` and `mkSpan`, use manual `pos <~> ... <~> pos` captures for delimited spans, use `Text` bridge for plain text.

```scala
package io.github.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.atomic
import parsley.character.string
import parsley.combinator.{many, manyTill}
import parsley.errors.combinator.ErrorMethods
import parsley.position.pos

import io.github.eleven19.ascribe.ast.{Bold, Inline, InlineContent, Italic, Mono, Span, Text, mkSpan}
import io.github.eleven19.ascribe.lexer.AsciiDocLexer.*

/** Parsers for inline AsciiDoc elements.
  *
  * Inline content sits inside headings, paragraphs, and list items. This module handles unconstrained markup spans
  * (double-delimiter pairs) and falls back to plain text for everything else.
  *
  * ==Supported markup==
  *   - `**bold**` -- [[Bold]]
  *   - `__italic__` -- [[Italic]]
  *   - ` ``mono`` ` -- [[Mono]]
  *   - everything else -- [[Text]]
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
    // Inline element parsers
    // -----------------------------------------------------------------------

    /** Parses an unconstrained **bold** span: `**content**`.
      * Uses manual pos captures since delimitedContent returns String, not List[Inline].
      */
    val boldSpan: Parsley[Inline] =
        (pos <~> delimitedContent("**", "**") <~> pos).map { case ((s, content), e) =>
            val span = mkSpan(s, e)
            Bold(List(Text(content)(span)))(span)
        }
            .label("bold span")
            .explain("Bold text is surrounded by double asterisks, e.g. **bold**")

    /** Parses an unconstrained _italic_ span: `__content__`. */
    val italicSpan: Parsley[Inline] =
        (pos <~> delimitedContent("__", "__") <~> pos).map { case ((s, content), e) =>
            val span = mkSpan(s, e)
            Italic(List(Text(content)(span)))(span)
        }
            .label("italic span")
            .explain("Italic text is surrounded by double underscores, e.g. __italic__")

    /** Parses an unconstrained `monospace` span: ` ``content`` `. */
    val monoSpan: Parsley[Inline] =
        (pos <~> delimitedContent("``", "``") <~> pos).map { case ((s, content), e) =>
            val span = mkSpan(s, e)
            Mono(List(Text(content)(span)))(span)
        }
            .label("monospace span")
            .explain("Monospace text is surrounded by double backticks, e.g. ``mono``")

    /** Parses one or more unadorned prose characters as a [[Text]] node. */
    val plainTextInline: Parsley[Inline] =
        Text(plainText)
            .label("text")

    /** Fallback for a single markup character that did not open a valid span. */
    val unpairedMarkupInline: Parsley[Inline] =
        (pos <~> unpairedMarkupChar <~> pos).map { case ((s, c), e) =>
            Text(c.toString)(mkSpan(s, e))
        }.hide

    /** Parses a single inline element (one of the above parsers in priority order). */
    val inlineElement: Parsley[Inline] =
        boldSpan | italicSpan | monoSpan | plainTextInline | unpairedMarkupInline

    /** Parses zero or more inline elements, stopping naturally at a newline or end-of-input. */
    val lineContent: Parsley[InlineContent] = many(inlineElement)
```

- [ ] **Step 2: Verify it compiles**

Run: `./mill ascribe.compile`
Expected: SUCCESS (or may fail if BlockParser/DocumentParser not yet updated — that's OK)

- [ ] **Step 3: Commit**

```bash
git add ascribe/src/io/github/eleven19/ascribe/parser/InlineParser.scala
git commit -m "refactor: update InlineParser with position tracking"
```

### Task 5: Update BlockParser to use bridge constructors

**Files:**
- Modify: `ascribe/src/io/github/eleven19/ascribe/parser/BlockParser.scala`

- [ ] **Step 1: Rewrite BlockParser.scala**

Replace the entire file contents. Key changes: use `Heading(...)`, `UnorderedList(...)`, etc. as bridge constructors; `ListItem` uses bridge for items.

```scala
package io.github.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.{atomic, notFollowedBy}
import parsley.character.{char, string}
import parsley.combinator.some
import parsley.errors.combinator.ErrorMethods

import io.github.eleven19.ascribe.ast.{Block, Heading, InlineContent, ListItem, OrderedList, Paragraph, UnorderedList}
import io.github.eleven19.ascribe.lexer.AsciiDocLexer.*
import io.github.eleven19.ascribe.parser.InlineParser.*

/** Parsers for block-level AsciiDoc elements. */
object BlockParser:

    // -----------------------------------------------------------------------
    // Headings
    // -----------------------------------------------------------------------

    /** Parses the leading `=`-markers of a heading and returns the heading level (1-5). */
    private val headingLevel: Parsley[Int] =
        atomic(string("=====")).as(5) |
            atomic(string("====")).as(4) |
            atomic(string("===")).as(3) |
            atomic(string("==")).as(2) |
            atomic(string("=")).as(1)

    /** Parses a section heading using the Heading bridge constructor. */
    val heading: Parsley[Block] =
        atomic(Heading(headingLevel <* char(' '), lineContent <* eolOrEof))
            .label("heading")
            .explain(
                "A heading starts with one to five equals signs followed by a space, e.g. = Title"
            )

    // -----------------------------------------------------------------------
    // Lists
    // -----------------------------------------------------------------------

    /** Parses a single unordered list item line: `* content`. */
    private val unorderedItem: Parsley[ListItem] =
        atomic(ListItem(char('*') *> char(' ') *> lineContent <* eolOrEof))
            .label("unordered list item")

    /** Parses one or more consecutive `* item` lines as an [[UnorderedList]]. */
    val unorderedList: Parsley[Block] =
        UnorderedList(some(unorderedItem).map(_.toList))
            .label("unordered list")

    /** Parses a single ordered list item line: `. content`. */
    private val orderedItem: Parsley[ListItem] =
        atomic(ListItem(char('.') *> char(' ') *> lineContent <* eolOrEof))
            .label("ordered list item")

    /** Parses one or more consecutive `. item` lines as an [[OrderedList]]. */
    val orderedList: Parsley[Block] =
        OrderedList(some(orderedItem).map(_.toList))
            .label("ordered list")

    // -----------------------------------------------------------------------
    // Paragraphs
    // -----------------------------------------------------------------------

    /** Negative lookahead for any block-starting prefix. */
    private val notBlockStart: Parsley[Unit] =
        notFollowedBy(headingLevel *> char(' ')) *>
            notFollowedBy(char('*') *> char(' ')) *>
            notFollowedBy(char('.') *> char(' '))

    /** Parses a single non-empty, non-block-start line as a list of inline elements. */
    private val paragraphLine: Parsley[InlineContent] =
        (notBlockStart *> some(inlineElement) <* eolOrEof).label("paragraph line")

    /** Parses one or more consecutive paragraph lines, joining their inline content. */
    val paragraph: Parsley[Block] =
        Paragraph(some(paragraphLine).map(_.flatten))
            .label("paragraph")
```

- [ ] **Step 2: Verify it compiles**

Run: `./mill ascribe.compile`
Expected: SUCCESS (or may fail if DocumentParser not yet updated)

- [ ] **Step 3: Commit**

```bash
git add ascribe/src/io/github/eleven19/ascribe/parser/BlockParser.scala
git commit -m "refactor: update BlockParser with bridge constructors"
```

### Task 6: Update DocumentParser to use bridge constructor

**Files:**
- Modify: `ascribe/src/io/github/eleven19/ascribe/parser/DocumentParser.scala`

- [ ] **Step 1: Rewrite DocumentParser.scala**

```scala
package io.github.eleven19.ascribe.parser

import parsley.Parsley
import parsley.Parsley.eof
import parsley.combinator.{option, sepEndBy, some}

import io.github.eleven19.ascribe.ast.Document
import io.github.eleven19.ascribe.lexer.AsciiDocLexer.blankLine
import io.github.eleven19.ascribe.parser.BlockParser.*

/** Top-level parser for a complete AsciiDoc document. */
object DocumentParser:

    /** One or more consecutive blank lines used as a block separator. */
    private val blankLines: Parsley[Unit] = some(blankLine).void

    /** Recognises any one block, trying block types in priority order. */
    private val block: Parsley[io.github.eleven19.ascribe.ast.Block] =
        heading | unorderedList | orderedList | paragraph

    /** Parses a complete AsciiDoc document from start to end of input.
      * The Document bridge constructor captures position before and after parsing.
      */
    val document: Parsley[Document] =
        Document(option(blankLines) *> sepEndBy(block, blankLines).map(_.toList) <* eof)
```

- [ ] **Step 2: Verify it compiles**

Run: `./mill ascribe.compile`
Expected: SUCCESS — all source code should now compile

- [ ] **Step 3: Commit**

```bash
git add ascribe/src/io/github/eleven19/ascribe/parser/DocumentParser.scala
git commit -m "refactor: update DocumentParser with bridge constructor"
```

## Chunk 4: Test Updates

### Task 7: Create TestHelpers and update unit tests

**Files:**
- Create: `ascribe/test/src/io/github/eleven19/ascribe/TestHelpers.scala`
- Modify: `ascribe/test/src/io/github/eleven19/ascribe/AscribeSpec.scala`
- Modify: `ascribe/test/src/io/github/eleven19/ascribe/parser/DocumentParserSpec.scala`
- Modify: `ascribe/test/src/io/github/eleven19/ascribe/parser/InlineParserSpec.scala`

- [ ] **Step 1: Create TestHelpers.scala**

```scala
package io.github.eleven19.ascribe

import io.github.eleven19.ascribe.ast.*

/** Convenience constructors for building AST nodes in tests without specifying positions. */
object TestHelpers:
  private val u = Span.unknown

  def text(s: String): Text = Text(s)(u)
  def bold(inlines: Inline*): Bold = Bold(inlines.toList)(u)
  def italic(inlines: Inline*): Italic = Italic(inlines.toList)(u)
  def mono(inlines: Inline*): Mono = Mono(inlines.toList)(u)
  def listItem(inlines: Inline*): ListItem = ListItem(inlines.toList)(u)
  def heading(level: Int, inlines: Inline*): Heading = Heading(level, inlines.toList)(u)
  def paragraph(inlines: Inline*): Paragraph = Paragraph(inlines.toList)(u)
  def unorderedList(items: ListItem*): UnorderedList = UnorderedList(items.toList)(u)
  def orderedList(items: ListItem*): OrderedList = OrderedList(items.toList)(u)
  def document(blocks: Block*): Document = Document(blocks.toList)(u)
```

- [ ] **Step 2: Rewrite AscribeSpec.scala**

```scala
package io.github.eleven19.ascribe

import parsley.{Failure, Success}
import zio.test.*

import io.github.eleven19.ascribe.TestHelpers.*

object AscribeSpec extends ZIOSpecDefault:

    def spec = suite("Ascribe public API")(
        test("parses a simple heading document") {
            Ascribe.parse("= Hello World\n") match
                case Success(doc) =>
                    assertTrue(doc == document(heading(1, text("Hello World"))))
                case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
        },
        test("parses a paragraph") {
            Ascribe.parse("Hello world.\n") match
                case Success(doc) =>
                    assertTrue(doc == document(paragraph(text("Hello world."))))
                case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
        },
        test("empty input produces an empty document") {
            Ascribe.parse("") match
                case Success(doc) => assertTrue(doc == document())
                case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
        }
    )
```

- [ ] **Step 3: Rewrite DocumentParserSpec.scala**

```scala
package io.github.eleven19.ascribe.parser

import parsley.{Failure, Success}
import zio.test.*

import io.github.eleven19.ascribe.TestHelpers.*
import io.github.eleven19.ascribe.parser.DocumentParser.document as parseDocument

object DocumentParserSpec extends ZIOSpecDefault:
    private def parse(input: String) = parseDocument.parse(input)

    def spec = suite("DocumentParser")(
        suite("headings")(
            test("parses a level-1 heading") {
                parse("= Title\n") match
                    case Success(doc) =>
                        assertTrue(doc == document(heading(1, text("Title"))))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses a level-3 heading") {
                parse("=== Section\n") match
                    case Success(doc) =>
                        assertTrue(doc == document(heading(3, text("Section"))))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses a heading with inline bold") {
                parse("== **Bold** Title\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == document(
                                heading(2, bold(text("Bold")), text(" Title"))
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("paragraphs")(
            test("parses a single-line paragraph") {
                parse("Hello world.\n") match
                    case Success(doc) =>
                        assertTrue(doc == document(paragraph(text("Hello world."))))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses a paragraph with inline markup") {
                parse("Use **parsley** to parse.\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == document(
                                paragraph(text("Use "), bold(text("parsley")), text(" to parse."))
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("unordered lists")(
            test("parses a single-item unordered list") {
                parse("* item one\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == document(unorderedList(listItem(text("item one"))))
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses a multi-item unordered list") {
                parse("* alpha\n* beta\n* gamma\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == document(
                                unorderedList(
                                    listItem(text("alpha")),
                                    listItem(text("beta")),
                                    listItem(text("gamma"))
                                )
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("ordered lists")(
            test("parses a multi-item ordered list") {
                parse(". first\n. second\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == document(
                                orderedList(listItem(text("first")), listItem(text("second")))
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("multi-block documents")(
            test("parses heading followed by paragraph") {
                parse("= Title\n\nIntroduction text.\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == document(
                                heading(1, text("Title")),
                                paragraph(text("Introduction text."))
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses heading, paragraph and list") {
                val input = "= Guide\n\nRead the steps:\n\n* step one\n* step two\n"
                parse(input) match
                    case Success(doc) =>
                        assertTrue(
                            doc == document(
                                heading(1, text("Guide")),
                                paragraph(text("Read the steps:")),
                                unorderedList(
                                    listItem(text("step one")),
                                    listItem(text("step two"))
                                )
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        )
    )
```

- [ ] **Step 4: Rewrite InlineParserSpec.scala**

```scala
package io.github.eleven19.ascribe.parser

import parsley.{Failure, Success}
import zio.test.*

import io.github.eleven19.ascribe.TestHelpers.*
import io.github.eleven19.ascribe.ast.Inline
import io.github.eleven19.ascribe.parser.InlineParser.lineContent

object InlineParserSpec extends ZIOSpecDefault:
    private def parse(input: String) = lineContent.parse(input)

    def spec = suite("InlineParser")(
        suite("plain text")(
            test("parses a simple word") {
                parse("hello") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(text("hello")))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses a sentence with spaces") {
                parse("hello world") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(text("hello world")))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("stops at newline") {
                parse("line one\nline two") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(text("line one")))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("bold spans")(
            test("parses **bold** text") {
                parse("**bold**") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(bold(text("bold"))))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses bold embedded in text") {
                parse("hello **world** end") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(text("hello "), bold(text("world")), text(" end"))
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("treats lone * as plain text") {
                parse("a*b") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(text("a"), text("*"), text("b")))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("italic spans")(
            test("parses __italic__ text") {
                parse("__italic__") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(italic(text("italic"))))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("monospace spans")(
            test("parses ``mono`` text") {
                parse("``mono``") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(mono(text("mono"))))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("mixed inline")(
            test("parses bold and italic together") {
                parse("**b** and __i__") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(bold(text("b")), text(" and "), italic(text("i")))
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        )
    )
```

- [ ] **Step 5: Run unit tests**

Run: `./mill ascribe.test`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add ascribe/test/src/io/github/eleven19/ascribe/TestHelpers.scala
git add ascribe/test/src/io/github/eleven19/ascribe/AscribeSpec.scala
git add ascribe/test/src/io/github/eleven19/ascribe/parser/DocumentParserSpec.scala
git add ascribe/test/src/io/github/eleven19/ascribe/parser/InlineParserSpec.scala
git commit -m "test: update all unit tests for refactored AST with TestHelpers"
```

### Task 8: Update integration tests (itest)

**Files:**
- Modify: `ascribe/itest/src/io/github/eleven19/ascribe/AsciiDocParserSteps.scala`

- [ ] **Step 1: Update pattern matches in AsciiDocParserSteps.scala**

All `Block.Heading`, `Block.Paragraph`, etc. become `Heading`, `Paragraph`, etc. (top-level case classes, not enum cases). Same for `Inline.Text`, `Inline.Bold`, etc.

Change the import from:
```scala
import io.github.eleven19.ascribe.ast.{Block, Document, Inline, ListItem}
```
to:
```scala
import io.github.eleven19.ascribe.ast.*
```

Then update all pattern matches throughout the file:
- `Block.Heading(l, inlines)` → `Heading(l, inlines)`
- `Block.Paragraph(inlines)` → `Paragraph(inlines)`
- `Block.UnorderedList(items)` → `UnorderedList(items)`
- `Block.OrderedList(items)` → `OrderedList(items)`
- `b: Block.UnorderedList` → `b: UnorderedList`
- `Inline.Text(s)` → `Text(s)`
- `Inline.Bold(cs)` → `Bold(cs)`
- `Inline.Italic(cs)` → `Italic(cs)`
- `Inline.Mono(cs)` → `Mono(cs)`

- [ ] **Step 2: Run integration tests**

Run: `./mill ascribe.itest`
Expected: All integration tests pass

- [ ] **Step 3: Run full test suite**

Run: `./mill ascribe.test && ./mill ascribe.itest`
Expected: All tests pass

- [ ] **Step 4: Verify formatting**

Run: `./mill ascribe.checkFormat`
Expected: No formatting issues (run `./mill ascribe.reformat` if needed)

- [ ] **Step 5: Commit**

```bash
git add ascribe/itest/src/io/github/eleven19/ascribe/AsciiDocParserSteps.scala
git commit -m "refactor: update itest pattern matches for refactored AST"
```

## Chunk 5: Verification

### Task 9: Full verification and format check

- [ ] **Step 1: Full compilation**

Run: `./mill __.compile`
Expected: All modules compile (249 tasks SUCCESS)

- [ ] **Step 2: All tests**

Run: `./mill ascribe.test && ./mill ascribe.itest`
Expected: All tests pass

- [ ] **Step 3: Format check**

Run: `./mill ascribe.checkFormat`
Expected: No formatting violations. If violations exist, run `./mill __.reformat` then commit.

- [ ] **Step 4: Verify position tracking works**

Add a quick manual check: parse a simple document and inspect the span values on the resulting AST nodes. This is not a committed test — just verification that bridges are capturing positions.

Run in the Mill REPL or a scratch test:
```scala
import io.github.eleven19.ascribe.Ascribe
import parsley.Success
Ascribe.parse("= Title\n") match
  case Success(doc) =>
    println(s"Document span: ${doc.span}")
    println(s"Heading span: ${doc.blocks.head.span}")
```

Expected: Spans should have non-zero line/col values (e.g., `Span(Position(1,1), Position(1,8))` or similar).
