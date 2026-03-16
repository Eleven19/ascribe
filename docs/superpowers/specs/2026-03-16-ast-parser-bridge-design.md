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
package io.github.eleven19.ascribe.ast

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

Parsley 4.6.2 provides `parsley.generic.ParserBridge0` through `ParserBridge22` and `ParserSingletonBridge` (pure bridges, no position tracking). It also provides `parsley.position.pos` which returns `Parsley[(Int, Int)]` (line, col). We define position-aware wrappers that capture `pos` before and after parsing:

> **Note:** Parsley also offers `parsley.position.withSpan`, but we define custom traits to produce our specific `Span` type and maintain full control over the position capture lifecycle.

```scala
package io.github.eleven19.ascribe.ast

import parsley.Parsley
import parsley.position.pos

private def mkSpan(s: (Int, Int), e: (Int, Int)): Span =
  Span(Position(s._1, s._2), Position(e._1, e._2))

trait PosParserBridge0[+A]:
  def apply()(span: Span): A
  def parser: Parsley[A] =
    (pos <~> pos).map { case (s, e) => apply()(mkSpan(s, e)) }

trait PosParserBridge1[-A, +B]:
  def apply(a: A)(span: Span): B
  def apply(a: Parsley[A]): Parsley[B] =
    (pos <~> a <~> pos).map { case ((s, a), e) => apply(a)(mkSpan(s, e)) }

trait PosParserBridge2[-A, -B, +C]:
  def apply(a: A, b: B)(span: Span): C
  def apply(a: Parsley[A], b: Parsley[B]): Parsley[C] =
    (pos <~> a <~> b <~> pos).map { case (((s, a), b), e) => apply(a, b)(mkSpan(s, e)) }

trait PosParserBridge3[-A, -B, -C, +D]:
  def apply(a: A, b: B, c: C)(span: Span): D
  def apply(a: Parsley[A], b: Parsley[B], c: Parsley[C]): Parsley[D] =
    (pos <~> a <~> b <~> c <~> pos).map { case ((((s, a), b), c), e) => apply(a, b, c)(mkSpan(s, e)) }
```

Each trait:
- Captures `pos` before and after parsing the arguments
- Constructs a `Span` from the start/end `(Int, Int)` tuples
- Passes it to the case class via the second parameter list
- The abstract `apply(values)(span)` must be implemented by each companion object

Additional arities added as needed.

### Refactored AST Types

**File:** `ascribe/src/io/github/eleven19/ascribe/ast/Document.scala`

Enums become sealed traits with case classes. Each case class has `(val span: Span)` in a second parameter list.

**Equality semantics:** Scala's auto-generated `equals`/`hashCode` for case classes only considers the first parameter list. This means two nodes with identical content but different spans compare as equal. This is intentional — tests should assert on structure, not positions.

**`CanEqual`:** Each case class derives `CanEqual` individually (rather than the sealed trait), since `derives CanEqual` on a sealed trait does not automatically cover separately-defined case class subtypes.

**Companion `apply`:** Each companion object must explicitly implement the bridge trait's abstract `apply` method using `new`. Extending a bridge trait suppresses the compiler's auto-generated `apply` for case classes.

```scala
package io.github.eleven19.ascribe.ast

type InlineContent = List[Inline]

sealed trait Inline:
  def span: Span

case class Text(content: String)(val span: Span) extends Inline derives CanEqual
case class Bold(content: List[Inline])(val span: Span) extends Inline derives CanEqual
case class Italic(content: List[Inline])(val span: Span) extends Inline derives CanEqual
case class Mono(content: List[Inline])(val span: Span) extends Inline derives CanEqual

object Text extends PosParserBridge1[String, Text]:
  def apply(content: String)(span: Span): Text = new Text(content)(span)

object Bold extends PosParserBridge1[List[Inline], Bold]:
  def apply(content: List[Inline])(span: Span): Bold = new Bold(content)(span)

object Italic extends PosParserBridge1[List[Inline], Italic]:
  def apply(content: List[Inline])(span: Span): Italic = new Italic(content)(span)

object Mono extends PosParserBridge1[List[Inline], Mono]:
  def apply(content: List[Inline])(span: Span): Mono = new Mono(content)(span)

case class ListItem(content: InlineContent)(val span: Span) derives CanEqual

object ListItem extends PosParserBridge1[InlineContent, ListItem]:
  def apply(content: InlineContent)(span: Span): ListItem = new ListItem(content)(span)

sealed trait Block:
  def span: Span

case class Heading(level: Int, title: InlineContent)(val span: Span) extends Block derives CanEqual
case class Paragraph(content: InlineContent)(val span: Span) extends Block derives CanEqual
case class UnorderedList(items: List[ListItem])(val span: Span) extends Block derives CanEqual
case class OrderedList(items: List[ListItem])(val span: Span) extends Block derives CanEqual

object Heading extends PosParserBridge2[Int, InlineContent, Heading]:
  def apply(level: Int, title: InlineContent)(span: Span): Heading = new Heading(level, title)(span)

object Paragraph extends PosParserBridge1[InlineContent, Paragraph]:
  def apply(content: InlineContent)(span: Span): Paragraph = new Paragraph(content)(span)

object UnorderedList extends PosParserBridge1[List[ListItem], UnorderedList]:
  def apply(items: List[ListItem])(span: Span): UnorderedList = new UnorderedList(items)(span)

object OrderedList extends PosParserBridge1[List[ListItem], OrderedList]:
  def apply(items: List[ListItem])(span: Span): OrderedList = new OrderedList(items)(span)

case class Document(blocks: List[Block])(val span: Span) derives CanEqual

object Document extends PosParserBridge1[List[Block], Document]:
  def apply(blocks: List[Block])(span: Span): Document = new Document(blocks)(span)
```

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

// After — bridge captures pos before and after automatically
val heading: Parsley[Block] =
    atomic(Heading(headingLevel <* char(' '), lineContent <* eolOrEof))
        .label("heading")
        .explain("A heading starts with one to five equals signs followed by a space, e.g. = Title")
```

```scala
// Before
some(unorderedItem).map(items => Block.UnorderedList(items.toList))

// After
UnorderedList(some(unorderedItem).map(_.toList))
```

**`InlineParser` — manual position capture for delimited spans:**

The current `delimitedContent` returns `Parsley[String]`, not `Parsley[List[Inline]]`. Recursive inline parsing within delimiters is a significant parser change better suited for a follow-up. For now, inline spans use explicit `pos` captures instead of bridges:

```scala
// Before
delimitedContent("**", "**")
    .map(s => Inline.Bold(List(Inline.Text(s))))

// After — manual pos capture wrapping both Bold and inner Text
val boldSpan: Parsley[Inline] =
    (pos <~> delimitedContent("**", "**") <~> pos).map { case ((s, content), e) =>
      val span = mkSpan(s, e)
      Bold(List(Text(content)(span)))(span)
    }
```

The outer `Bold` and inner `Text` share the same span (the delimiter boundaries). When recursive inline parsing is added later, the inner nodes will get their own accurate spans.

`plainTextInline` and `unpairedMarkupInline` use similar manual `pos` captures since they produce `Text` nodes directly.

**`DocumentParser` example:**

```scala
// Before
(option(blankLines) *> sepEndBy(block, blankLines) <* eof)
    .map(blocks => Document(blocks.toList))

// After — bridge captures pos at start (before leading blanks) and end (after eof)
Document(option(blankLines) *> sepEndBy(block, blankLines).map(_.toList) <* eof)
```

Note: `.map(_.toList)` binds to `sepEndBy(...)` only (higher precedence than `*>`), which is the intended behavior.

Existing `.label()` and `.explain()` calls remain unchanged.

### Test Updates

**File:** `ascribe/test/src/io/github/eleven19/ascribe/TestHelpers.scala`

Convenience constructors for tests where position is irrelevant:

```scala
package io.github.eleven19.ascribe

import io.github.eleven19.ascribe.ast.*

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

Existing tests update to use these helpers. Tests that verify parsing should assert on structure (block types, content values) without matching on exact span values — positions may shift as the parser evolves. Equality comparisons work without considering spans because Scala's case class `equals` only examines the first parameter list.

## Scope

**In scope:**
- `Position`, `Span` types with `Span.unknown` sentinel
- `PosParserBridge` traits (arities 0–3+) wrapping Parsley's `parsley.position.pos`
- Refactor AST from enums to sealed traits + case classes with `(val span: Span)`
- Bridge companion objects with explicit `apply` implementations on all AST types
- Update `DocumentParser`, `BlockParser` to use bridge constructors
- Update `InlineParser` with manual `pos` captures (bridges deferred to recursive inline parsing follow-up)
- Update all existing tests to compile with the new AST
- `TestHelpers` for test convenience

**Out of scope:**
- ASG module + JSON serialization (separate sub-project, depends on this)
- Advanced error messages (verified/preventative errors)
- Recursive inline parsing within delimiters
- AST/ASG construction DSL (ascribe-soo)
- Expanding AST to cover additional TCK constructs (sections, sidebars, etc.)
