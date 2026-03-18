---
title: ASG Model Guide
---

# ASG Model Guide

The Abstract Semantic Graph (ASG) is Ascribe's output model, matching the official AsciiDoc specification's semantic schema. While the AST captures parser-level structure, the ASG represents the document in the canonical form expected by the AsciiDoc TCK.

## ASG vs AST

| Aspect | AST | ASG |
|--------|-----|-----|
| Purpose | Parser output | Canonical document model |
| Positions | `Span` (past-end) | `Location` (inclusive, 1-based) |
| Headings | Flat `Heading` nodes | Nested `Section` containers |
| Inline markup | `Bold`, `Italic`, `Mono` | `Span` with `variant`/`form` |
| Lists | `UnorderedList`/`OrderedList` | `List` with `variant`/`marker` |
| JSON | Not serializable | Schema-derived codecs |

## Node Type Hierarchy

All ASG types are defined in `io.eleven19.ascribe.asg`:

```scala
sealed trait Node derives Schema:
  def location: Location
  def nodeType: String    // "block", "inline", or "string"

sealed trait Block extends Node   // all block-level content
sealed trait Inline extends Node  // all inline content
```

### Block Types

- **Document** -- top-level container (extends `Node` directly, not `Block`)
- **Section** -- heading + nested blocks, with `level`
- **Heading** -- discrete heading (not part of a section)
- **Paragraph** -- contains `inlines: Chunk[Inline]`
- **Listing, Literal, Pass, Stem, Verse** -- verbatim/special blocks with `form`, `delimiter`, `inlines`
- **Sidebar, Example, Admonition, Open, Quote** -- parent blocks with `form`, `delimiter`, `blocks`
- **List, DList** -- ordered/unordered and description lists
- **ListItem, DListItem** -- list entries with `marker`, `principal`
- **Break** -- thematic/page breaks
- **Audio, Video, Image, Toc** -- block macros

### Inline Types

- **Span** -- formatting (strong, emphasis, code, mark) with `variant` and `form` (constrained/unconstrained)
- **Ref** -- links and cross-references with `variant`, `target`
- **Text** -- plain text content (`nodeType = "string"`)
- **CharRef** -- character references (`nodeType = "string"`)
- **Raw** -- passthrough content (`nodeType = "string"`)

## Schema-Derived Codecs

JSON serialization uses `zio-blocks-schema`:

```scala
object AsgCodecs:
  private val codec = summon[Schema[Node]].derive(
    JsonBinaryCodecDeriver
      .withDiscriminatorKind(DiscriminatorKind.Field("name"))
      .withCaseNameMapper(NameMapper.Custom(mapCaseName))
      .withTransientDefaultValue(true)
  )

  def encode(node: Node): String = new String(codec.encode(node).toArray)
  def decode(json: String): Either[String, Node] = ...
```

Key aspects:
- **`DiscriminatorKind.Field("name")`** -- The type discriminator is a `"name"` field in the JSON, e.g., `"name": "paragraph"`.
- **`mapCaseName`** -- Converts Scala case class names to ASG names: `Paragraph` becomes `"paragraph"`, `DList` becomes `"dlist"`, `CharRef` becomes `"charref"`.
- **`withTransientDefaultValue(true)`** -- Fields with `None` or empty default values are omitted from JSON output.

## Private Constructors and Smart Apply

ASG case classes have `private` constructors to enforce invariants:

```scala
case class Paragraph private (
  id: Option[String],
  title: Option[Chunk[Inline]],
  // ...
  @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Paragraph:
  def apply(
    id: Option[String] = None,
    // ...
    location: Location
  ): Paragraph = new Paragraph(id, ..., location, "block")
```

The `nodeType` field (serialized as `"type"` in JSON) is always set to the correct value (`"block"`, `"inline"`, or `"string"`) by the companion `apply` method.

## Location Type

`Location` wraps start and end `Position` values. It serializes as a JSON array rather than an object, matching the TCK schema:

```scala
case class Location(start: Position, end: Position)

object Location:
  given Schema[Location] = summon[Schema[Chunk[Position]]].transform[Location](
    chunk => Location(chunk(0), chunk(1)),
    loc => Chunk(loc.start, loc.end)
  )
```

This produces JSON like: `"location": [[1, 1], [3, 15]]`

`Position` has 1-based `line` and `col` fields, with an optional `file` for multi-file documents.
