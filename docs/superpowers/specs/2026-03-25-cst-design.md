# CST (Concrete Syntax Tree) Design

## Goal

Add a Concrete Syntax Tree layer to Ascribe that preserves the full syntactic structure of AsciiDoc source, including include directives, comments, blank lines, and line boundaries within paragraphs. The CST enables:

- Include directives as first-class inspectable/transformable nodes
- Best-effort roundtrip rendering (CST → source text → CST produces equivalent structure)
- A foundation for source formatting, linting, and IDE tooling

## Architecture

### Pipeline Flow

The processing pipeline changes from:

```
Source → IncludeProcessor (text-level) → Parser → AST → Bridge → ASG
```

to:

```
Source → Parser → CST → IncludeResolver (CST→CST) → CstLowering → AST → Bridge → ASG
```

The text-level `IncludeProcessor` is replaced by a CST-level `IncludeResolver` that operates on typed nodes instead of regex over raw text.

### Module Placement

The CST types live in the core `ascribe` module alongside the AST, in package `io.eleven19.ascribe.cst`. No new module is needed — the CST is a lower-level representation of the same source, sharing `Span`, `Position`, and parser infrastructure.

## CST Type Hierarchy

```scala
package io.eleven19.ascribe.cst

sealed trait CstNode:
  def span: Span

// -- Top-level ---------------------------------------------------------------

/** A top-level content node is either a block or a blank line. */
sealed trait CstTopLevel extends CstNode

case class CstDocument(
  header: Option[CstDocumentHeader],
  content: List[CstTopLevel]
)(val span: Span) extends CstNode

case class CstDocumentHeader(
  title: CstHeading,
  attributes: List[CstAttributeEntry]
)(val span: Span) extends CstNode

// -- Block-level nodes -------------------------------------------------------

sealed trait CstBlock extends CstTopLevel

case class CstHeading(
  level: Int,
  marker: String,        // the raw "==" characters (level derived from marker.length)
  title: List[CstInline]
)(val span: Span) extends CstBlock

case class CstParagraph(
  lines: List[CstParagraphLine]
)(val span: Span) extends CstBlock

case class CstParagraphLine(
  content: List[CstInline]
)(val span: Span) extends CstNode

case class CstDelimitedBlock(
  kind: DelimitedBlockKind,
  delimiter: String,
  content: CstBlockContent,
  attributes: Option[CstAttributeList],
  title: Option[CstBlockTitle]
)(val span: Span) extends CstBlock

enum DelimitedBlockKind:
  case Listing, Literal, Sidebar, Example, Quote, Open, Pass, Comment

sealed trait CstBlockContent:
  def span: Span

case class CstVerbatimContent(raw: String)(val span: Span) extends CstBlockContent
case class CstNestedContent(children: List[CstTopLevel])(val span: Span) extends CstBlockContent

case class CstList(
  variant: ListVariant,
  items: List[CstListItem]
)(val span: Span) extends CstBlock

enum ListVariant:
  case Unordered, Ordered

case class CstListItem(
  marker: String,         // "* " or ". " — preserves the raw marker
  content: List[CstInline]
)(val span: Span) extends CstNode

case class CstTable(
  rows: List[CstTableRow],
  delimiter: String,      // "|===" for normal tables, "!===" for nested tables
  format: TableFormat,
  attributes: Option[CstAttributeList],
  title: Option[CstBlockTitle],
  hasBlankAfterFirstRow: Boolean
)(val span: Span) extends CstBlock

// Include directive — first-class node
case class CstInclude(
  target: String,
  attributes: CstAttributeList
)(val span: Span) extends CstBlock

// Single-line comment: // content
case class CstLineComment(
  content: String
)(val span: Span) extends CstBlock

// Attribute entry: :name: value
case class CstAttributeEntry(
  name: String,
  value: String
)(val span: Span) extends CstBlock

// Blank line — preserved whitespace
case class CstBlankLine()(val span: Span) extends CstTopLevel

// -- Block metadata ----------------------------------------------------------

case class CstAttributeList(
  positional: List[String],
  named: Map[String, String],
  options: List[String],
  roles: List[String]
)(val span: Span) extends CstNode

case class CstBlockTitle(
  content: List[CstInline]
)(val span: Span) extends CstNode

// -- Inline nodes ------------------------------------------------------------

sealed trait CstInline extends CstNode

case class CstText(content: String)(val span: Span) extends CstInline

case class CstBold(
  content: List[CstInline],
  constrained: Boolean       // true = *text*, false = **text**
)(val span: Span) extends CstInline

case class CstItalic(
  content: List[CstInline]
)(val span: Span) extends CstInline

case class CstMono(
  content: List[CstInline]
)(val span: Span) extends CstInline

// -- Table sub-nodes ---------------------------------------------------------

case class CstTableRow(
  cells: List[CstTableCell]
)(val span: Span) extends CstNode

case class CstTableCell(
  content: CstCellContent,
  style: Option[String],
  colSpan: Option[Int],
  rowSpan: Option[Int],
  dupFactor: Option[Int]
)(val span: Span) extends CstNode

sealed trait CstCellContent:
  def span: Span

case class CstCellInlines(content: List[CstInline])(val span: Span) extends CstCellContent
case class CstCellBlocks(content: List[CstTopLevel])(val span: Span) extends CstCellContent
```

### Key Differences from AST

| Aspect | CST | AST |
|--------|-----|-----|
| Include directives | `CstInclude` node | Not present (resolved before parsing) |
| Single-line comments | `CstLineComment` node | Not present |
| Block comments | `CstDelimitedBlock(Comment, ...)` | `Comment` node |
| Blank lines | `CstBlankLine` node | Consumed as separators |
| Paragraph lines | `CstParagraphLine` per line | Merged into single `InlineContent` |
| Bold variants | `CstBold(constrained: Boolean)` | Separate `Bold`/`ConstrainedBold` types |
| Sections | Not present (flat headings) | `Section` nodes (restructured) |
| Attribute entries | `CstAttributeEntry` nodes at block level | Processed into header attributes |
| Delimited blocks | Unified `CstDelimitedBlock` with `kind` enum | Separate types per block kind |
| Top-level typing | `CstTopLevel` restricts to blocks + blank lines | `List[Block]` (no blank lines) |

### Known Limitations

- **Constrained italic/mono**: The current parser only supports constrained bold (`*text*`), not constrained italic (`_text_`) or constrained monospace (`` `text` ``). `CstItalic` and `CstMono` will need a `constrained: Boolean` field when those parsers are added. Out of scope for this iteration.
- **List item block content**: `CstListItem.content` is `List[CstInline]`. AsciiDoc supports block content in list items via list continuation (`+`), which is not yet supported by the parser. This is a known gap.
- **Description lists**: Labeled/definition lists (`term:: definition`) are not supported by the current parser and are not addressed in the CST.

## Parser Changes

### Strategy

Retarget the existing Parsley parser to emit CST nodes. The parser combinators, lexer, and bridge traits stay the same — only the output types and node constructors change.

### Migration Safety

Before retargeting the parser, generate golden AST outputs for all existing test inputs. After retargeting, verify that `CstLowering.toAst(Ascribe.parseCst(input))` produces identical results to the golden outputs. This ensures no regressions.

### What Changes

1. **`BlockParser`** emits `CstBlock` variants instead of AST `Block` variants
2. **`InlineParser`** emits `CstInline` variants instead of AST `Inline` variants
3. **`DocumentParser`** emits `CstDocument` — no longer calls `restructure` (moves to lowering)
4. **`notBlockStart`** guard extended to also reject:
   - Lines starting with `//` (single-line comments)
   - Lines starting with `include::` (include directives)
   - Lines matching `:key: value` (attribute entries)
5. **New parsers added:**
   - Single-line `//` comment → `CstLineComment`
   - `include::target[attrs]` → `CstInclude`
   - Blank lines captured as `CstBlankLine` instead of consumed
   - Standalone `:key: value` lines → `CstAttributeEntry`
6. **`CstInclude` recognized inside compound blocks** — `compoundInnerBlock` (used by sidebar, example, quote, open) must include the new parsers for `include::`, `//` comments, and `:key: value` entries alongside existing block parsers.

### What Stays the Same

- All Parsley combinators (delimiter matching, inline parsing, table parsing)
- `AsciiDocLexer` primitives
- `PosParserBridge` traits
- Error messages and backtracking behavior

### Public API

```scala
object Ascribe:
  // Existing — unchanged behavior, now goes through CST internally
  def parse(source: String): Result[String, Document]

  // New — returns the CST for callers who need it
  def parseCst(source: String): Result[String, CstDocument]
```

All existing callers of `Ascribe.parse` continue working. Internally, `parse` calls `parseCst` then `CstLowering.toAst`.

## CST → AST Lowering

Pure function: `CstLowering.toAst(cst: CstDocument): Document`

Transformations:
1. Drop `CstBlankLine` nodes
2. Drop `CstLineComment` nodes
3. Drop `CstDelimitedBlock(Comment, ...)` nodes
4. Merge `CstParagraphLine` sequences into `Paragraph` with joined `InlineContent`
5. Split `CstBold(constrained=true)` → `ConstrainedBold`, `CstBold(constrained=false)` → `Bold`
6. Convert `CstDelimitedBlock(kind, ...)` → corresponding AST type (`Listing`, `Sidebar`, etc.)
7. Convert `CstList(Unordered, ...)` → `UnorderedList`, `CstList(Ordered, ...)` → `OrderedList`
8. Convert `CstTable` → `Table`, mapping `CstTableRow`/`CstTableCell` to AST equivalents
9. Call `restructure` to build `Section` hierarchy from flat headings
10. Error on unresolved `CstInclude` nodes (should be resolved before lowering)
11. Drop body-level `CstAttributeEntry` nodes (the AST has no representation for body-level attribute entries; they are only preserved in `CstDocumentHeader.attributes` → `DocumentHeader.attributes`)

The lowering must produce identical AST output to what the current parser produces, so all existing tests pass without modification.

## Include Resolution

### IncludeResolver

```scala
object IncludeResolver:
  def resolve(
    cst: CstDocument,
    baseDir: Path,
    maxDepth: Int = 64
  ): CstDocument < (Sync & Abort[PipelineError])
```

Walks the CST looking for `CstInclude` nodes. For each:
1. Resolve the target path relative to `baseDir`
2. Read the file content
3. Parse to CST via `Ascribe.parseCst`
4. Splice the resulting `CstDocument.content` nodes in place of the `CstInclude`
5. Recurse into the spliced content (respecting depth limit)
6. Handle `opts=optional` — missing files produce empty splice instead of error

Include directives inside compound blocks (sidebar, example, quote, open) are resolved the same way — the resolver walks into `CstNestedContent.children`.

### Pipeline Integration

`FileSource` changes from:

```
read file → IncludeProcessor.process(text) → Ascribe.parse(expanded) → AST
```

to:

```
read file → Ascribe.parseCst(text) → IncludeResolver.resolve(cst) → CstLowering.toAst(resolved) → AST
```

The text-level `IncludeProcessor` is removed.

## Roundtrip Rendering

```scala
object CstRenderer:
  def render(cst: CstDocument): String
```

Reconstructs source text from CST nodes:

- `CstBlankLine` → empty line
- `CstHeading(level, marker, title)` → marker + space + rendered title + newline
- `CstDelimitedBlock(kind, delim, content, attrs, title)` → optional attrs + optional title + delimiter + newline + content + delimiter + newline
- `CstParagraph(lines)` → each line rendered + newline
- `CstInclude(target, attrs)` → `include::target[rendered attrs]`
- `CstLineComment(content)` → `// content`
- `CstAttributeEntry(name, value)` → `:name: value`
- `CstList(variant, items)` → each item with marker + content
- `CstTable` → attribute list + title + delimiter + rows + delimiter

Best-effort fidelity: significant whitespace preserved (blank lines between blocks, line breaks within paragraphs). Trailing spaces and exact whitespace sequences are not guaranteed to be byte-identical.

## CST Visitor

Following the existing `AstVisitor` pattern:

```scala
trait CstVisitor[A]:
  def visitNode(node: CstNode): A

  // Category defaults
  def visitTopLevel(node: CstTopLevel): A = visitNode(node)
  def visitBlock(node: CstBlock): A = visitTopLevel(node)
  def visitInline(node: CstInline): A = visitNode(node)

  // Document
  def visitDocument(node: CstDocument): A = visitNode(node)
  def visitDocumentHeader(node: CstDocumentHeader): A = visitNode(node)

  // Block types
  def visitHeading(node: CstHeading): A = visitBlock(node)
  def visitParagraph(node: CstParagraph): A = visitBlock(node)
  def visitDelimitedBlock(node: CstDelimitedBlock): A = visitBlock(node)
  def visitList(node: CstList): A = visitBlock(node)
  def visitTable(node: CstTable): A = visitBlock(node)
  def visitInclude(node: CstInclude): A = visitBlock(node)
  def visitLineComment(node: CstLineComment): A = visitBlock(node)
  def visitAttributeEntry(node: CstAttributeEntry): A = visitBlock(node)
  def visitBlankLine(node: CstBlankLine): A = visitTopLevel(node)

  // Sub-block nodes
  def visitParagraphLine(node: CstParagraphLine): A = visitNode(node)
  def visitListItem(node: CstListItem): A = visitNode(node)
  def visitBlockTitle(node: CstBlockTitle): A = visitNode(node)
  def visitAttributeList(node: CstAttributeList): A = visitNode(node)
  def visitTableRow(node: CstTableRow): A = visitNode(node)
  def visitTableCell(node: CstTableCell): A = visitNode(node)

  // Inline types
  def visitText(node: CstText): A = visitInline(node)
  def visitBold(node: CstBold): A = visitInline(node)
  def visitItalic(node: CstItalic): A = visitInline(node)
  def visitMono(node: CstMono): A = visitInline(node)
```

Companion object provides `foldLeft`, `foldRight`, `collect`, `count` — stack-safe via trampolining, same as `AstVisitor`.

Extension methods on `CstNode` for convenient usage.

## Testing Strategy

### Migration Safety Net

Before retargeting the parser, generate golden AST outputs (as serialized data or snapshot files) for all existing test inputs. After retargeting, verify `CstLowering.toAst(Ascribe.parseCst(input))` produces structurally identical results. This is the single most important test for safe migration.

### Existing Tests

All existing AST-level tests pass unchanged. `Ascribe.parse` returns the same `Document` — it just goes through CST → lowering internally now.

### New CST Tests

1. **CST structure tests** — parse source, verify CST nodes preserve:
   - Blank lines as `CstBlankLine` nodes
   - Single-line `//` comments as `CstLineComment`
   - `include::` directives as `CstInclude` nodes (without resolution)
   - Individual paragraph lines as `CstParagraphLine`
   - Attribute entries as `CstAttributeEntry`
   - Delimited block delimiters and kinds

2. **Lowering equivalence tests** — compare `CstLowering.toAst(parseCst(input))` against golden AST outputs for all existing test corpus inputs

3. **Include resolution tests** — replace `IncludeProcessorSpec` with CST-level equivalents testing `IncludeResolver.resolve`

4. **Roundtrip tests** — `CstRenderer.render(Ascribe.parseCst(source))` parses back to equivalent CST structure

5. **Visitor tests** — `CstVisitor` fold/collect operations work correctly

## Scope Exclusions

The following are explicitly out of scope for this iteration:

- **Conditional directives** (`ifdef`/`ifndef`/`endif`) — can be added later as a new `CstBlock` variant.
- **Byte-identical roundtrip** — trailing whitespace, exact newline sequences (`\n` vs `\r\n`), and other insignificant whitespace differences are acceptable.
- **CST-level ASG bridge** — the bridge continues to work on AST. CST → AST → ASG is the path.
- **Attribute substitution** — `{attribute-name}` references in content are not resolved at CST level.
- **Constrained italic/mono** — not yet supported by the parser; deferred.
- **List continuation** (`+`) — block content in list items is not yet supported; deferred.
- **Description lists** (`term:: definition`) — not yet supported by the parser; deferred.
