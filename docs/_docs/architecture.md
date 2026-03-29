---
title: Architecture
---

# Architecture

## Pipeline

Ascribe processes AsciiDoc through a linear pipeline:

```
Source (String / Files)
  |
  v
Parser (Parsley combinators)
  |
  v
AST (io.eleven19.ascribe.ast)
  |
  v
Rewrite Rules (optional transforms)
  |
  v
Bridge (AstToAsg.convert)
  |
  v
ASG (io.eleven19.ascribe.asg)
  |
  v
Renderers (AsciiDoc, JSON, custom)
  |
  v
Sink (String / Files)
```

Each stage is a pure function or a Kyo effect. The parser produces an AST with source positions, rewrite rules can transform the AST, the bridge converts it to the ASG model matching the official AsciiDoc schema, and renderers produce output in various formats.

## Module Structure

Mill modules live under the `ascribe/` directory (nested names use dots: `ascribe/core/` → `ascribe.core`, `ascribe/pipeline/kyo/` → `ascribe.pipeline.kyo`).

| Module | Mill | Artifact | Package | Purpose |
|--------|------|----------|---------|---------|
| Core | `ascribe.core` | `ascribe-core` | `io.eleven19.ascribe` | Parser, lexer, AST types, AST visitor, DSL |
| ASG | `ascribe.asg` | `ascribe-asg` | `io.eleven19.ascribe.asg` | ASG node types, JSON codecs, ASG visitor, DSL |
| Bridge | `ascribe.bridge` | `ascribe-bridge` | `io.eleven19.ascribe.bridge` | AST-to-ASG converter |
| Pipeline (core) | `ascribe.pipeline.core` | `ascribe-pipeline-core` | `io.eleven19.ascribe.pipeline.core` | `PipelineOp`, `RewriteRule`, pure rewrites |
| Pipeline (Kyo) | `ascribe.pipeline.kyo` | `ascribe-pipeline-kyo` | `io.eleven19.ascribe.pipeline` | Kyo-backed pipeline, file I/O, includes |
| Pipeline (HTML) | `ascribe.pipeline.html` | `ascribe-pipeline-html` | `io.eleven19.ascribe.pipeline.html` | scalatags HTML output |
| Pipeline (Markdown) | `ascribe.pipeline.markdown` | `ascribe-pipeline-markdown` | `io.eleven19.ascribe.pipeline.markdown` | GFM via zio-blocks-docs |
| Pipeline (Ox) | `ascribe.pipeline.ox` | `ascribe-pipeline-ox` | `io.eleven19.ascribe.pipeline.ox` | Ox-backed runtime (optional) |
| TCK Runner | `ascribe.tck-runner` | — | `build.ascribe.tckrunner` | TCK test harness (Cucumber) — not published |

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
  |     +-- Listing (delimiter, content: String, attributes, title)
  |     +-- Literal (delimiter, content: String, attributes, title)
  |     +-- Sidebar (delimiter, blocks, attributes, title)
  |     +-- Example (delimiter, blocks, attributes, title)
  |     +-- Quote (delimiter, blocks, attributes, title)
  |     +-- Open (delimiter, blocks, attributes, title)
  |     +-- Pass (delimiter, content: String, attributes, title)
  |     +-- Comment (delimiter, content: String)
  |     +-- UnorderedList (items: List[ListItem])
  |     +-- OrderedList (items: List[ListItem])
  |     +-- Table (rows, delimiter, format, attributes, title, hasBlankAfterFirstRow)
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

Delimited blocks (Listing, Literal, Sidebar, Example, Quote, Open, Pass) support variable-length fences and nesting. Content-only blocks (Listing, Literal, Pass, Comment) capture their body as a raw string; container blocks (Sidebar, Example, Quote, Open) parse their body as nested blocks.

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
- **Kyo effects in pipeline**: The pipeline module uses Kyo's effect system for composable I/O, allowing pipelines to be constructed without committing to a specific execution strategy.
- **Shared build traits**: Compiler settings (including `-Werror`) are centralized in `CommonScalaModule` and `CommonScalaTestModule` meta-build traits.
