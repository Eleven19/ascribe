---
title: Architecture
---

# Architecture

## Pipeline

Ascribe processes AsciiDoc through a linear pipeline:

```
Source (String)
  |
  v
Parser (Parsley combinators)
  |
  v
AST (io.eleven19.ascribe.ast)
  |
  v
Bridge (AstToAsg.convert)
  |
  v
ASG (io.eleven19.ascribe.asg)
  |
  v
JSON (AsgCodecs.encode)
```

Each stage is a pure function. The parser produces an AST with source positions, the bridge converts it to the ASG model matching the official AsciiDoc schema, and the codec serializes it to TCK-compatible JSON.

## Module Structure

The project is organized into four modules:

| Module | Package | Purpose |
|--------|---------|---------|
| `ascribe` | `io.eleven19.ascribe` | Parser, lexer, AST types, AST visitor |
| `ascribe.asg` | `io.eleven19.ascribe.asg` | ASG node types, JSON codecs, ASG visitor |
| `ascribe.bridge` | `io.eleven19.ascribe.bridge` | AST-to-ASG converter |
| `ascribe.tck-runner` | `build.ascribe.tckrunner` | TCK test harness (Cucumber) |

## AST Type Hierarchy

The parser produces an AST rooted at `AstNode`:

```
AstNode
  +-- Document (header: Option[DocumentHeader], blocks: List[Block])
  +-- DocumentHeader (title: InlineContent, attributes: List[(String, String)])
  +-- Block
  |     +-- Heading (level, title)
  |     +-- Section (level, title, blocks)
  |     +-- Paragraph (content: InlineContent)
  |     +-- ListingBlock (delimiter, content: String)
  |     +-- SidebarBlock (delimiter, blocks)
  |     +-- UnorderedList (items: List[ListItem])
  |     +-- OrderedList (items: List[ListItem])
  |     +-- TableBlock (attributes: AttributeList, title: Option[BlockTitle], rows: List[TableRow], format: TableFormat)
  +-- TableRow (cells: List[TableCell])
  +-- TableCell (content: CellContent)   -- CellContent = Inlines | Blocks
  +-- AttributeList, BlockTitle, TableFormat
  +-- Inline
  |     +-- Text (content: String)
  |     +-- Bold (content: List[Inline])
  |     +-- ConstrainedBold (content: List[Inline])
  |     +-- Italic (content: List[Inline])
  |     +-- Mono (content: List[Inline])
  +-- ListItem (content: InlineContent)
```

All AST nodes carry a `Span` (start/end source positions).

## ASG Type Hierarchy

The ASG is a richer model matching the AsciiDoc specification schema:

```
Node (sealed trait)
  +-- Document (attributes, header, blocks)
  +-- Block (sealed trait)
  |     +-- Section, Heading, Paragraph, Listing, Literal, Pass, Stem, Verse
  |     +-- Sidebar, Example, Admonition, Open, Quote
  |     +-- List, DList, ListItem, DListItem
  |     +-- Table (cols, rows), TableRow (cells), TableCell (style, colspan, rowspan, content)
  |     +-- ColumnSpec, CellStyle, ColSpan, RowSpan, DupCount
  |     +-- Break, Audio, Video, Image, Toc
  +-- Inline (sealed trait)
        +-- Span (variant, form, inlines) -- formatting spans
        +-- Ref (variant, target, inlines) -- links/xrefs
        +-- Text (value) -- plain text
        +-- CharRef (value) -- character references
        +-- Raw (value) -- passthrough content
```

All ASG nodes carry a `Location` (start/end `Position` with 1-based line and column).

## Key Design Decisions

- **Sealed traits**: Both AST and ASG hierarchies use sealed traits, enabling exhaustive pattern matching and safe `derives Schema` derivation.
- **Schema.derived codecs**: ASG JSON serialization uses `zio-blocks-schema` with `DiscriminatorKind.Field("name")` to produce TCK-compatible JSON without hand-written codecs.
- **Private constructors + smart apply**: ASG case classes have `private` constructors. Companion `apply` methods set the `nodeType` field automatically (always `"block"`, `"inline"`, or `"string"`), preventing invalid states.
- **Position tracking via ParserBridges**: The parser uses custom `PosParserBridge` traits that capture Parsley source positions and thread them into AST node constructors.
- **Section restructuring**: The parser emits flat headings; `DocumentParser.restructure` groups them into nested `Section` trees based on heading level.
- **Location as array**: The ASG `Location` type serializes as a JSON array `[[startLine, startCol], [endLine, endCol]]` via `Schema.transform`, matching the TCK schema.
