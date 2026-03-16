# ASG Module + JSON Serialization Design

**Date:** 2026-03-16
**Issue:** ascribe-12m — Add ASG JSON serialization to AST types

## Problem

The TCK tests need to compare parser output against expected ASG JSON. The current AST has no JSON serialization and its structure doesn't match the ASG format. A separate ASG model is needed that mirrors the official AsciiDoc ASG schema, with bidirectional JSON codecs and a converter from the parser's AST.

## Design

### Module Structure

Three modules with clean separation of concerns:

| Module | Package | Depends on | Contains |
|--------|---------|-----------|----------|
| `ascribe` | `io.github.eleven19.ascribe` | parsley | AST types, parser |
| `ascribe/asg` | `io.eleven19.ascribe.asg` | zio-blocks-schema | ASG types, JSON codecs |
| `ascribe/bridge` | `io.eleven19.ascribe.bridge` | ascribe + ascribe/asg | AST → ASG converter |

The ASG module has no dependency on the parser or AST — it's a standalone model of the AsciiDoc ASG format usable by anyone working with ASG JSON. The bridge module is the only place that knows about both.

### ASG Type Hierarchy

Based on the official ASG JSON schema at `asciidoc-lang/asg/schema.json`. Uses `zio.Chunk` as the collection type.

#### Base Types

```scala
package io.eleven19.ascribe.asg

import zio.Chunk

type Location = Chunk[Position]

case class Position(line: Int, col: Int, file: Option[Chunk[String]] = None)

sealed abstract class Node(val name: String, val nodeType: String):
  def location: Location

sealed abstract class Block(name: String) extends Node(name, "block"):
  def id: Option[String]
  def title: Option[Chunk[Inline]]
  def reftext: Option[Chunk[Inline]]
  def metadata: Option[BlockMetadata]

sealed abstract class Inline(name: String, nodeType: String) extends Node(name, nodeType)
```

- `name` and `nodeType` are immutable vals on the abstract classes, computed from the type — impossible to construct a `Paragraph` with name `"section"`
- `Block` provides common optional fields (`id`, `title`, `reftext`, `metadata`) as abstract defs with `None` defaults on concrete subtypes
- `Inline` takes both `name` and `nodeType` since inline literals use `"string"` while parent inlines use `"inline"`
- `Position` includes the optional `file` field from the schema's `locationBoundary`

#### Supporting Types

```scala
case class BlockMetadata(
  attributes: Map[String, String] = Map.empty,
  options: Chunk[String] = Chunk.empty,
  roles: Chunk[String] = Chunk.empty,
  location: Option[Location] = None
)

case class Header(
  title: Option[Chunk[Inline]] = None,
  authors: Chunk[Author] = Chunk.empty,
  location: Option[Location] = None
)

case class Author(
  fullname: Option[String] = None,
  initials: Option[String] = None,
  firstname: Option[String] = None,
  middlename: Option[String] = None,
  lastname: Option[String] = None,
  address: Option[String] = None
)
```

`Header` and `Author` are plain case classes — not part of the `Node` hierarchy since they have no `name`/`type` discriminator in the ASG schema. All `Header` fields are optional per the schema (no `required` array on the header definition).

#### Document (root — extends Node directly, not Block)

The ASG schema defines `Document` separately from `abstractBlock` — it has `attributes` and `header` but NOT `id`, `title`, `reftext`, or `metadata`. It extends `Node` directly:

```scala
case class Document(
  attributes: Option[Map[String, Option[String]]] = None,
  header: Option[Header] = None,
  blocks: Chunk[Block] = Chunk.empty,
  location: Location
) extends Node("document", "block")
```

Note: `attributes` uses `Map[String, Option[String]]` because the schema allows null values (used for unset attributes in AsciiDoc).

#### Block Types

**Section and Heading** — both derive from `abstractHeading` which requires `title` and `level`:

```scala
case class Section(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  level: Int,
  blocks: Chunk[Block] = Chunk.empty,
  location: Location
) extends Block("section")

case class Heading(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  level: Int,
  location: Location
) extends Block("heading")
```

Note: The schema declares `title` and `level` as required on `abstractHeading`, but in our Scala model `title` remains `Option` on the `Block` base class. In practice, valid ASG data for sections and headings always has a title. Validation is handled at the codec/schema level, not the type level.

**Leaf blocks** (paragraph, listing, literal, pass, stem, verse) — blocks containing inline content:

```scala
case class Paragraph(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  form: Option[String] = None,
  delimiter: Option[String] = None,
  inlines: Chunk[Inline] = Chunk.empty,
  location: Location
) extends Block("paragraph")

case class Listing(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  form: Option[String] = None,
  delimiter: Option[String] = None,
  inlines: Chunk[Inline] = Chunk.empty,
  location: Location
) extends Block("listing")

// Same pattern for Literal, Pass, Stem, Verse
```

`form` and `delimiter` are optional on all leaf blocks. The schema has a conditional rule: when `form` is `"delimited"`, `delimiter` is required. This is enforced at the codec/validation level, not the type level.

**Parent blocks** (sidebar, example, admonition, open, quote) — blocks containing child blocks:

```scala
case class Sidebar(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  form: String,
  delimiter: String,
  blocks: Chunk[Block] = Chunk.empty,
  location: Location
) extends Block("sidebar")

case class Example(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  form: String,
  delimiter: String,
  blocks: Chunk[Block] = Chunk.empty,
  location: Location
) extends Block("example")

case class Admonition(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  form: String,
  delimiter: String,
  variant: String,
  blocks: Chunk[Block] = Chunk.empty,
  location: Location
) extends Block("admonition")

case class Open(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  form: String,
  delimiter: String,
  blocks: Chunk[Block] = Chunk.empty,
  location: Location
) extends Block("open")

case class Quote(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  form: String,
  delimiter: String,
  blocks: Chunk[Block] = Chunk.empty,
  location: Location
) extends Block("quote")
```

Parent blocks require `form` and `delimiter` (non-optional).

**Lists:**

```scala
case class List(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  variant: String,
  marker: String,
  items: Chunk[ListItem],
  location: Location
) extends Block("list")

case class DList(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  marker: String,
  items: Chunk[DListItem],
  location: Location
) extends Block("dlist")

case class ListItem(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  marker: String,
  principal: Chunk[Inline],
  blocks: Chunk[Block] = Chunk.empty,
  location: Location
) extends Block("listItem")

case class DListItem(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  marker: String,
  terms: Chunk[Chunk[Inline]],
  principal: Option[Chunk[Inline]] = None,
  blocks: Chunk[Block] = Chunk.empty,
  location: Location
) extends Block("dlistItem")
```

Note: `DListItem.principal` is `Option` (not required in the schema), while `ListItem.principal` is required.

**Block macros** — split into concrete types since each has a fixed `name` value (required for schema derivation):

```scala
case class Break(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  variant: String,
  location: Location
) extends Block("break")

case class Audio(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  form: String = "macro",
  target: Option[String] = None,
  location: Location
) extends Block("audio")

case class Video(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  form: String = "macro",
  target: Option[String] = None,
  location: Location
) extends Block("video")

case class Image(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  form: String = "macro",
  target: Option[String] = None,
  location: Location
) extends Block("image")

case class Toc(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  reftext: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  form: String = "macro",
  target: Option[String] = None,
  location: Location
) extends Block("toc")
```

Each macro type has a fixed `name`, making them compatible with sealed hierarchy schema derivation.

#### Inline Types

```scala
// Parent inlines (contain child inlines, type = "inline")
case class Span(
  variant: String,
  form: String,
  inlines: Chunk[Inline],
  location: Location
) extends Inline("span", "inline")

case class Ref(
  variant: String,
  target: String,
  inlines: Chunk[Inline],
  location: Location
) extends Inline("ref", "inline")

// Literal inlines (leaf nodes with string values, type = "string")
case class Text(
  value: String,
  location: Location
) extends Inline("text", "string")

case class CharRef(
  value: String,
  location: Location
) extends Inline("charref", "string")

case class Raw(
  value: String,
  location: Location
) extends Inline("raw", "string")
```

### JSON Codecs

Uses `zio-blocks-schema` for codec derivation with `Schema.derived` and `JsonEncoder`/`JsonDecoder`.

Custom codec handling is needed for:
- The `name` and `type` discriminator fields (derived from the sealed hierarchy, not stored as data)
- The `location` field format (`[{line, col}, {line, col}]`)
- Optional fields that should be omitted from JSON when `None`
- `Document.attributes` allowing null values (`Map[String, Option[String]]`)

Codecs are bidirectional — encode ASG to JSON for comparing parser output, decode TCK expected JSON into ASG types for structured comparison.

For TCK test comparison, `JsonDiffer.diff(expected, actual)` produces a `JsonPatch` — if empty, the ASG matches; if not, the patch operations describe exactly what differs.

### AST → ASG Converter

**File:** `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala`

A recursive function that converts `ast.Document` → `asg.Document`:

```scala
object AstToAsg:
  def convert(doc: ast.Document): asg.Document = ...
```

Mapping from current AST types:

| AST Type | ASG Type | Notes |
|----------|----------|-------|
| `ast.Document` | `asg.Document` | |
| `ast.Paragraph` | `asg.Paragraph` | |
| `ast.Heading` | `asg.Section` | AST levels 1-5 map directly to ASG levels; see note below |
| `ast.UnorderedList` | `asg.List(variant = "unordered")` | `marker = "*"` |
| `ast.OrderedList` | `asg.List(variant = "ordered")` | `marker = "."` |
| `ast.ListItem` | `asg.ListItem` | |
| `ast.Text` | `asg.Text` | |
| `ast.Bold` | `asg.Span(variant = "strong", form = "unconstrained")` | |
| `ast.Italic` | `asg.Span(variant = "emphasis", form = "unconstrained")` | |
| `ast.Mono` | `asg.Span(variant = "code", form = "unconstrained")` | |

**Level mapping note:** The ASG schema allows section levels starting at 0 (for document title `=`). The AST parser currently produces heading levels 1-5 (for `=` through `=====`). The exact mapping (whether to subtract 1 or pass through directly) should be determined by comparing against TCK expected output during implementation.

**Heading vs Section:** The AST's `ast.Heading` maps to `asg.Section` (a section heading that contains child blocks). The ASG also has `asg.Heading` for discrete headings (headings not associated with a section). The current parser does not distinguish between these, so all headings map to `asg.Section` for now.

Position conversion: `ast.Span(start, end)` → `Chunk(asg.Position(start.line, start.col), asg.Position(end.line, end.col))`.

The converter only handles AST types that exist today. ASG types not yet produced by the parser exist in the type system for TCK test assertions but won't be produced by the converter until the parser supports them.

## Scope

**In scope:**
- `ascribe/asg` module with full ASG type hierarchy per the official schema
- `zio-blocks-schema` JSON codecs (encode + decode)
- `ascribe/bridge` module with AST → ASG converter
- Tests for codecs (roundtrip) and converter
- Package: `io.eleven19.ascribe.asg` and `io.eleven19.ascribe.bridge`

**Out of scope:**
- Visitor/fold for AST (ascribe-qmq)
- Visitor/fold for ASG (ascribe-kbu)
- AST/ASG construction DSL (ascribe-soo)
- Package migration for core module (ascribe-wh8)
- Wiring ASG into TCK test runner (ascribe-lnn — depends on this)
