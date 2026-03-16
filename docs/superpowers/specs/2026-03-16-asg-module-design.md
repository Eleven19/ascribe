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

case class Position(line: Int, col: Int)

sealed abstract class Node(val name: String, val nodeType: String):
  def location: Location

sealed abstract class Block(name: String) extends Node(name, "block"):
  def id: Option[String]
  def title: Option[Chunk[Inline]]
  def metadata: Option[BlockMetadata]

sealed abstract class Inline(name: String, nodeType: String) extends Node(name, nodeType)
```

- `name` and `nodeType` are immutable vals on the abstract classes, computed from the type — impossible to construct a `Paragraph` with name `"section"`
- `Block` provides common optional fields (`id`, `title`, `metadata`) as abstract defs with `None` defaults on concrete subtypes
- `Inline` takes both `name` and `nodeType` since inline literals use `"string"` while parent inlines use `"inline"`

#### Supporting Types

```scala
case class BlockMetadata(
  attributes: Map[String, String] = Map.empty,
  options: Chunk[String] = Chunk.empty,
  roles: Chunk[String] = Chunk.empty,
  location: Option[Location] = None
)

case class Header(
  title: Chunk[Inline],
  authors: Chunk[Author] = Chunk.empty,
  location: Location
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

`Header` and `Author` are plain case classes — not part of the `Node` hierarchy since they have no `name`/`type` discriminator in the ASG schema.

#### Block Types

**Document (root):**
```scala
case class Document(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  attributes: Option[Map[String, String]] = None,
  header: Option[Header] = None,
  blocks: Chunk[Block] = Chunk.empty,
  location: Location
) extends Block("document")
```

**Section:**
```scala
case class Section(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  level: Int,
  blocks: Chunk[Block] = Chunk.empty,
  location: Location
) extends Block("section")
```

**Leaf blocks** (paragraph, listing, literal, pass, stem, verse) — blocks containing inline content:
```scala
case class Paragraph(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  inlines: Chunk[Inline] = Chunk.empty,
  location: Location
) extends Block("paragraph")

// Same pattern for Listing, Literal, Pass, Stem, Verse
// with additional form: Option[String] and delimiter: Option[String]
```

**Parent blocks** (sidebar, example, admonition, open, quote) — blocks containing child blocks:
```scala
case class Sidebar(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  form: String,
  delimiter: String,
  blocks: Chunk[Block] = Chunk.empty,
  location: Location
) extends Block("sidebar")

// Same pattern for Example, Open, Quote
// Admonition adds variant: String
```

**Lists:**
```scala
case class List(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  variant: String,
  marker: String,
  items: Chunk[ListItem],
  location: Location
) extends Block("list")

case class ListItem(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  marker: String,
  principal: Chunk[Inline],
  blocks: Chunk[Block] = Chunk.empty,
  location: Location
) extends Block("listItem")

// DList and DListItem follow similar patterns
```

**Other blocks:**
```scala
case class Heading(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  level: Int,
  location: Location
) extends Block("heading")

case class Break(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  variant: String,
  location: Location
) extends Block("break")

case class BlockMacro(
  id: Option[String] = None,
  title: Option[Chunk[Inline]] = None,
  metadata: Option[BlockMetadata] = None,
  macroName: String,
  form: String = "macro",
  target: Option[String] = None,
  location: Location
) extends Block(macroName)
```

Note: `BlockMacro` is the one type where `name` varies (`audio`, `video`, `image`, `toc`), so it takes `macroName` as a constructor argument passed to `Block(macroName)`.

#### Inline Types

```scala
// Parent inlines (contain child inlines)
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

// Literal inlines (leaf nodes with string values)
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

| AST Type | ASG Type |
|----------|----------|
| `ast.Document` | `asg.Document` |
| `ast.Paragraph` | `asg.Paragraph` |
| `ast.Heading` | `asg.Section` (level > 0) |
| `ast.UnorderedList` | `asg.List(variant = "unordered")` |
| `ast.OrderedList` | `asg.List(variant = "ordered")` |
| `ast.ListItem` | `asg.ListItem` |
| `ast.Text` | `asg.Text` |
| `ast.Bold` | `asg.Span(variant = "strong", form = "unconstrained")` |
| `ast.Italic` | `asg.Span(variant = "emphasis", form = "unconstrained")` |
| `ast.Mono` | `asg.Span(variant = "code", form = "unconstrained")` |

Position conversion: `ast.Span(start, end)` → `asg.Location(Chunk(Position(start.line, start.col), Position(end.line, end.col)))`.

The converter only handles AST types that exist today. ASG types not yet produced by the parser (section, sidebar, listing, etc.) exist in the type system for TCK test assertions but won't be produced by the converter until the parser supports them.

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
