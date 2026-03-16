# AST Parser Bridge Pattern + Position Tracking Design

**Date:** 2026-03-16
**Issue:** ascribe-12m (prerequisite) — Refactor AST with parser bridge pattern for position tracking

## Problem

The current AST uses Scala 3 enums (`enum Inline`, `enum Block`) with no source position tracking. The TCK ASG format requires `"location": [{line, col}, {line, col}]` on every node. Adding position tracking manually would clutter parser code with `pos` captures scattered throughout.

The Parsley parser bridge pattern solves this by encoding position capture into AST companion objects, keeping parser code clean. However, the pattern requires case classes with second parameter lists and companion objects extending bridge traits — incompatible with Scala 3 enums.

## Design

### Position Types

**File:** `ascribe/src/io/github/eleven19/ascribe/ast/Position.scala`

```scala
case class Position(line: Int, col: Int)

case class Span(start: Position, end: Position)

object Span:
  /** Sentinel for tests and contexts where position is irrelevant. */
  val unknown: Span = Span(Position(0, 0), Position(0, 0))
```

- `Position` maps to `{"line": N, "col": N}` in ASG JSON
- `Span` maps to the `"location": [{...}, {...}]` array (start + end)
- `Span.unknown` provides a sentinel for test construction

### Custom Parser Bridge Traits

**File:** `ascribe/src/io/github/eleven19/ascribe/ast/ParserBridges.scala`

Parsley 4.6.2 provides `parsley.generic.ParserBridge0` through `ParserBridge22` and `ParserSingletonBridge`. These are pure bridges with no position tracking. We define position-aware wrappers:

```scala
trait PosParserBridge0[+A]:
  def apply()(span: Span): A
  def con: Parsley[A] =
    (pos <~> pos).map { case (s, e) =>
      apply()(Span(Position(s._1, s._2), Position(e._1, e._2)))
    }

trait PosParserBridge1[-A, +B]:
  def apply(a: A)(span: Span): B
  def apply(a: Parsley[A]): Parsley[B] =
    (pos <~> a <~> pos).map { case ((s, a), e) =>
      apply(a)(Span(Position(s._1, s._2), Position(e._1, e._2)))
    }

trait PosParserBridge2[-A, -B, +C]:
  def apply(a: A, b: B)(span: Span): C
  def apply(a: Parsley[A], b: Parsley[B]): Parsley[C] =
    (pos <~> a <~> b <~> pos).map { case (((s, a), b), e) =>
      apply(a, b)(Span(Position(s._1, s._2), Position(e._1, e._2)))
    }
```

Each trait:
- Captures `pos` before and after parsing the arguments
- Constructs a `Span` from the start/end positions
- Passes it to the case class via the second parameter list
- Provides both direct `apply(values)(span)` for test/manual construction and `apply(parsers)` for parser use

Additional arities (`PosParserBridge3`, etc.) added as needed.

### Refactored AST Types

**File:** `ascribe/src/io/github/eleven19/ascribe/ast/Document.scala`

Enums become sealed traits with case classes. Each case class has `(val span: Span)` in a second parameter list (invisible to pattern matching):

```scala
type InlineContent = List[Inline]

sealed trait Inline derives CanEqual:
  def span: Span

case class Text(content: String)(val span: Span) extends Inline
case class Bold(content: List[Inline])(val span: Span) extends Inline
case class Italic(content: List[Inline])(val span: Span) extends Inline
case class Mono(content: List[Inline])(val span: Span) extends Inline

object Text extends PosParserBridge1[String, Text]
object Bold extends PosParserBridge1[List[Inline], Bold]
object Italic extends PosParserBridge1[List[Inline], Italic]
object Mono extends PosParserBridge1[List[Inline], Mono]

case class ListItem(content: InlineContent)(val span: Span) derives CanEqual

object ListItem extends PosParserBridge1[InlineContent, ListItem]

sealed trait Block derives CanEqual:
  def span: Span

case class Heading(level: Int, title: InlineContent)(val span: Span) extends Block
case class Paragraph(content: InlineContent)(val span: Span) extends Block
case class UnorderedList(items: List[ListItem])(val span: Span) extends Block
case class OrderedList(items: List[ListItem])(val span: Span) extends Block

object Heading extends PosParserBridge2[Int, InlineContent, Heading]
object Paragraph extends PosParserBridge1[InlineContent, Paragraph]
object UnorderedList extends PosParserBridge1[List[ListItem], UnorderedList]
object OrderedList extends PosParserBridge1[List[ListItem], OrderedList]

case class Document(blocks: List[Block])(val span: Span) derives CanEqual

object Document extends PosParserBridge1[List[Block], Document]
```

Key properties:
- `span` is abstract in sealed traits — accessible on any `Inline`/`Block` without casting
- Second parameter list keeps `span` invisible to pattern matching
- Companion objects extend bridge traits — used directly as parser constructors

### Parser Updates

Parsers switch from `yield`/`.map` construction to bridge constructor syntax.

**`BlockParser` examples:**

```scala
// Before
val heading: Parsley[Block] =
    atomic(
        for
            level <- headingLevel
            _     <- char(' ')
            title <- lineContent
            _     <- eolOrEof
        yield Block.Heading(level, title)
    )

// After
val heading: Parsley[Block] =
    atomic(Heading(headingLevel <* char(' '), lineContent <* eolOrEof))
```

```scala
// Before
some(unorderedItem).map(items => Block.UnorderedList(items.toList))

// After
UnorderedList(some(unorderedItem).map(_.toList))
```

**`InlineParser` examples:**

```scala
// Before
delimitedContent("**", "**").map(s => Inline.Bold(List(Inline.Text(s))))

// After — bold wraps parsed text content with bridge
```

**`DocumentParser` example:**

```scala
// Before
(option(blankLines) *> sepEndBy(block, blankLines) <* eof)
    .map(blocks => Document(blocks.toList))

// After
Document(option(blankLines) *> sepEndBy(block, blankLines).map(_.toList) <* eof)
```

Existing `.label()` and `.explain()` calls remain unchanged.

### Test Updates

**File:** `ascribe/test/src/io/github/eleven19/ascribe/TestHelpers.scala`

Convenience constructors for tests where position is irrelevant:

```scala
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

Existing tests update to use these helpers, or construct nodes directly with `Span.unknown`.

Tests that verify parsing should assert on structure (block types, content) without matching on exact spans — positions may shift as the parser evolves.

## Scope

**In scope:**
- `Position`, `Span` types with `Span.unknown` sentinel
- `PosParserBridge` traits wrapping Parsley's `parsley.generic` bridges
- Refactor AST from enums to sealed traits + case classes with `(val span: Span)`
- Bridge companion objects on all AST types
- Update `DocumentParser`, `BlockParser`, `InlineParser` to use bridge constructors
- Update all existing tests to compile with the new AST
- `TestHelpers` for test convenience

**Out of scope:**
- ASG module + JSON serialization (separate sub-project, depends on this)
- Advanced error messages (verified/preventative errors)
- AST/ASG construction DSL (new ticket)
- Expanding AST to cover additional TCK constructs (sections, sidebars, etc.)
