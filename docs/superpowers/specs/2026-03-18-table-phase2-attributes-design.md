# Table Phase 2: Attributes & Metadata — Design Spec

**Date:** 2026-03-18
**Status:** Draft
**Depends on:** Phase 1 (basic PSV tables, PR #15)

## Overview

Phase 2 extends table support with attribute lists, column specifications, header/footer rows, title, and display attributes (frame, grid, stripes). Column formatting (e.g., `a`, `m`, `e` styles) is deferred to Phase 3.

## Scope

### In Scope

1. **Block attribute list parser** — `[key=value, ...]` preceding any block (reusable, not table-specific)
2. **Block title parser** — `.Title text` line preceding blocks (also reusable)
3. **`cols` attribute** — column count, widths, horizontal alignment, vertical alignment
4. **Column multiplier** — `3*` syntax to repeat column specifiers
5. **Header row** — implicit (first row + blank line), explicit (`%header`), and suppression (`%noheader`)
6. **Footer row** — `%footer` option promoting last row
7. **Display attributes** — `frame` (all/ends/sides/none), `grid` (all/cols/rows/none), `stripes` (none/even/odd/hover/all)
8. **Table title** — `.My Table` rendered as table caption
9. **Custom TCK tests** for all new features

### Out of Scope (Phase 3+)

- Column formatting styles (`a`, `e`, `m`, `s`, `l`, `h`, `p`)
- Cell spanning (`2+`, `.2+`, `2.3+`)
- Cell duplication (`3*`)
- Per-cell alignment overrides
- DSV/CSV/TSV formats
- Nested tables (`!===`)
- AsciiDoc cell content (`a|`)

## Design

### Layer 1: AST Changes

#### New AST nodes

```scala
/** Opaque types for attribute list values — gives meaning to raw strings. */
object AttributeList:
    opaque type AttributeName  = String
    opaque type AttributeValue = String
    opaque type OptionName     = String
    opaque type RoleName       = String

    object AttributeName:
        def apply(s: String): AttributeName = s
        extension (n: AttributeName) def value: String = n

    object AttributeValue:
        def apply(s: String): AttributeValue = s
        extension (v: AttributeValue) def value: String = v

    object OptionName:
        def apply(s: String): OptionName = s
        extension (o: OptionName) def value: String = o

    object RoleName:
        def apply(s: String): RoleName = s
        extension (r: RoleName) def value: String = r

/** A parsed attribute list: [key=value, %option, .role] */
case class AttributeList(
    positional: List[AttributeList.AttributeValue],
    named: Map[AttributeList.AttributeName, AttributeList.AttributeValue],
    options: List[AttributeList.OptionName],
    roles: List[AttributeList.RoleName]
)(val span: Span) extends AstNode derives CanEqual

/** A block title: .Title text */
case class BlockTitle(content: InlineContent)(val span: Span) extends AstNode derives CanEqual
```

The opaque types prevent accidentally passing a role where an option is expected, or mixing up attribute names and values, while having zero runtime overhead. `BlockTitle` content is parsed using the existing inline content parser, so titles can contain inline formatting (bold, italic, etc.) per the AsciiDoc spec.

#### Modified AST nodes

```scala
case class TableBlock(
    rows: List[TableRow],
    delimiter: String,
    attributes: Option[AttributeList],
    title: Option[BlockTitle],
    hasBlankAfterFirstRow: Boolean    // for implicit header detection
)(val span: Span) extends Block derives CanEqual
```

**Note:** `AttributeList` and `BlockTitle` are general-purpose AST nodes. Other blocks (listing, sidebar, etc.) can adopt them later, but Phase 2 only wires them to `TableBlock`.

**Note:** Multiple stacked attribute lists (e.g., `[%header]` on one line, `[cols="1,2,3"]` on the next) are merged by the parser into a single `AttributeList`. Named attributes from later lines override earlier ones; options and roles accumulate. This matches Asciidoctor behavior.

### Layer 2: Parser Changes

#### Attribute list parser (new, in `BlockParser`)

Parses `[...]` lines appearing before block delimiters:

```
[cols="1,2,3"]                    → named: {cols → "1,2,3"}
[%header%footer,cols="2,2,1"]    → options: [header, footer], named: {cols → "2,2,1"}
[frame=ends,grid=rows]           → named: {frame → ends, grid → rows}
[cols="3*"]                      → named: {cols → "3*"}
[cols="<1,^2,>3"]                → named: {cols → "<1,^2,>3"}
[#myid.role1.role2%autowidth]    → named: {id → myid}, roles: [role1, role2], options: [autowidth]
```

Syntax rules (from AsciiDoc spec):
- `key=value` or `key="value"` for named attributes
- `%value` for shorthand options
- `#id` for element ID shorthand
- `.role` for role shorthand
- Positional attributes are comma-separated values without keys
- Multiple `[...]` lines are parsed individually then merged (later named attrs override, options/roles accumulate)

#### Block title parser (new, in `BlockParser`)

Parses `.Title text` lines (a dot followed by non-whitespace at start of line):

```
.My Table Title
```

Must not match delimiters like `....` (four dots = literal block delimiter). The title text is parsed using the existing inline content parser to support formatting.

#### Table block parser (modified)

The `tableBlock` parser is extended to optionally consume a preceding attribute list and/or title:

```
(.Title)?
([attributes])*     ← zero or more, merged into one AttributeList
|===
rows...
|===
```

Order: title appears before attribute list(s) (per AsciiDoc spec).

**Important:** The `notBlockStart` guard in the paragraph parser must be updated to also exclude `[` at line start (attribute lists) and `.` followed by non-whitespace (block titles), so the paragraph parser does not consume them.

#### `cols` attribute parsing

The `cols` value is parsed in the bridge layer (not the block parser), since it's semantic interpretation of a string value. The grammar for a single column specifier:

```
col-entry    = [multiplier] [h-align] [v-align] [width]
multiplier   = <integer> "*"
h-align      = "<" | "^" | ">"
v-align      = "." "<" | "." "^" | "." ">"
width        = <positive-integer>
```

A `cols` value is a comma-separated list of `col-entry` entries.

**Multiplier expansion:** The multiplier duplicates the entire remaining specifier N times. Examples:
- `3*` → three columns with default width/alignment
- `3*>` → three columns, each right-aligned
- `3*^.^10` → three columns, each center/middle aligned, width 10
- `5,3*` → one column width 5, then three default columns

### Layer 3: ASG Changes

#### New ASG types

```scala
/** Horizontal alignment for a table column or cell. */
enum HAlign derives Schema:
    case Left, Center, Right

/** Vertical alignment for a table column or cell. */
enum VAlign derives Schema:
    case Top, Middle, Bottom

/** Column specification parsed from the cols attribute. */
case class ColumnSpec(
    width: Option[Int],
    halign: Option[HAlign],
    valign: Option[VAlign]
) derives Schema
```

**Note:** The `Frame`, `Grid`, and `Stripes` enums are not needed as ASG types. The bridge validates these string values and stores them as `Option[String]` in the ASG, matching the JSON output format directly. Validation happens in the bridge; invalid values are ignored (matching Asciidoctor behavior).

#### Modified ASG nodes

```scala
case class Table private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: String,
    delimiter: String,
    columns: Option[Chunk[ColumnSpec]],   // NEW — parsed from cols attr
    header: Option[Chunk[Block]],         // NEW — header row (None if no header)
    rows: Chunk[Block],                   // body rows only (excluding header/footer)
    footer: Option[Chunk[Block]],         // NEW — footer row (None if no footer)
    frame: Option[String],               // NEW — "all", "ends", "sides", "none"
    grid: Option[String],                // NEW — "all", "cols", "rows", "none"
    stripes: Option[String],             // NEW — "none", "even", "odd", "hover", "all"
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema
```

**Field ordering:** `columns`, `header`, `rows`, `footer` are grouped to match visual table layout (top to bottom). Display attributes (`frame`, `grid`, `stripes`) follow.

**Design decision:** `frame`, `grid`, and `stripes` are stored as `Option[String]` rather than enums in the ASG. This matches the TCK JSON output format where they appear as string values.

**Design decision:** `header` and `footer` are `Option[Chunk[Block]]` (the cells of the promoted row) rather than wrapping in a `TableRow`. This simplifies the JSON output — header/footer cells appear directly under the table node. This is a deliberate asymmetry with `rows` (which contains `TableRow` wrappers); the rationale is that header/footer are single rows promoted to table-level fields, so the row wrapper is redundant.

**Design decision:** When `cols` is not specified, `columns` is `None` (omitted from JSON). Consumers determine column count from the first row. When `cols` is specified, `columns` contains the parsed column specs.

### Layer 4: Bridge Changes

The `AstToAsg` bridge gains:

1. **`cols` parser** — Parse the `cols` attribute string into `Chunk[ColumnSpec]`:
   - Split on `,`
   - For each entry, extract optional multiplier, h-align, v-align, width
   - Expand multipliers: the multiplier applies to the full remaining specifier

2. **Header row detection** (precedence order):
   - If `%noheader` option is present → **no header**, regardless of blank lines
   - If `%header` option is present → **first row promoted** to header
   - If neither option and `hasBlankAfterFirstRow` is true → **first row promoted** (implicit header)
   - Otherwise → **no header**

3. **Footer row detection**:
   - If `%footer` option is present → **last row promoted** to footer
   - Otherwise → **no footer**

4. **Attribute extraction** — Read `frame`, `grid`, `stripes` from the `AttributeList.named` map. Validate against known values; ignore invalid values.

5. **Title extraction** — Convert the `BlockTitle` inline content into the ASG `Table.title` field using the existing `convertInline` machinery.

6. **Metadata mapping** — The `AttributeList` is converted to `BlockMetadata` (which already has `attributes`, `options`, `roles` fields). This populates `Table.metadata`.

### Layer 5: DSL Updates

Extend both AST and ASG DSLs:

```scala
// AST DSL additions (in ast/dsl.scala)
def attributeList(named: (String, String)*): AttributeList
def blockTitle(text: String): BlockTitle
def tableBlock(attrs: Option[AttributeList], title: Option[BlockTitle], rows: TableRow*): TableBlock

// ASG DSL additions (in asg/dsl.scala)
def table(
    columns: Option[Chunk[ColumnSpec]] = None,
    header: Option[Chunk[Block]] = None,
    footer: Option[Chunk[Block]] = None,
    frame: Option[String] = None,
    grid: Option[String] = None,
    stripes: Option[String] = None,
    rows: Block*
)(using l: Location): Table
```

### Layer 6: Implicit Header Detection

The AsciiDoc spec defines implicit header as: the first line of content after `|===` is non-empty AND the second line is empty (blank line).

**Approach:** The parser tracks whether a blank line appears between the first and second rows. The `hasBlankAfterFirstRow` boolean on `TableBlock` captures this.

**Parser subtlety:** The current parser skips leading blank lines after `|===` via `option(some(blankLine)).void`. If the first line after `|===` is blank, there's no content on the first line, so implicit header does NOT trigger. The parser must distinguish:
- `|===\n| A | B\n\n| C | D\n|===` → `hasBlankAfterFirstRow = true` (implicit header)
- `|===\n\n| A | B\n| C | D\n|===` → `hasBlankAfterFirstRow = false` (leading blank, no implicit header)

The parser implementation will track whether any rows have been parsed before encountering the first blank line gap.

## Test Plan

Custom TCK tests under `tests/block/table/`:

| Test | Input | Validates |
|------|-------|-----------|
| `cols-equal` | `[cols="3*"]` + 3-col table | Column multiplier, equal widths |
| `cols-widths` | `[cols="1,2,3"]` + 3-col table | Proportional column widths |
| `cols-alignment` | `[cols="<,^,>"]` + 3-col table | Horizontal alignment operators |
| `cols-valign` | `[cols=".<,.^,.>"]` + 3-col table | Vertical alignment operators |
| `cols-mixed` | `[cols="<1,^2,>3"]` + 3-col table | Combined alignment + width |
| `cols-multiplier-align` | `[cols="5,3*>"]` + 4-col table | Mixed multiplier with alignment |
| `table-title` | `.My Table` + basic table | Title/caption rendering |
| `header-implicit` | First row + blank line | Implicit header detection |
| `header-explicit` | `[%header]` + table | Explicit header option |
| `header-noheader` | `[%noheader]` + blank-line table | Suppressed implicit header |
| `footer` | `[%footer]` + table | Footer row promotion |
| `header-footer` | `[%header%footer]` + table | Both header and footer |
| `frame-grid` | `[frame=ends,grid=rows]` + table | Frame/grid attributes |
| `stripes` | `[stripes=even]` + table | Stripes attribute |
| `full-attrs` | All attributes combined | Integration test |

Each test has an `-input.adoc` and `-output.json` file pair. Expected JSON output will include the new ASG fields (`columns`, `header`, `footer`, `frame`, `grid`, `stripes`).

## JSON Output Format

Example for `[%header,cols="<1,^2,>3",frame=ends,grid=rows]` with `.My Table`:

```json
{
  "name": "table",
  "title": [{ "name": "text", "value": "My Table", "type": "inline", "location": [...] }],
  "form": "delimited",
  "delimiter": "|===",
  "columns": [
    { "width": 1, "halign": "left" },
    { "width": 2, "halign": "center" },
    { "width": 3, "halign": "right" }
  ],
  "header": [
    { "name": "tableCell", "inlines": [...], "type": "block", "location": [...] },
    { "name": "tableCell", "inlines": [...], "type": "block", "location": [...] },
    { "name": "tableCell", "inlines": [...], "type": "block", "location": [...] }
  ],
  "frame": "ends",
  "grid": "rows",
  "rows": [...],
  "type": "block",
  "location": [...]
}
```

Fields that are `None` are omitted from JSON output (consistent with existing codec behavior).

## Implementation Order

1. AST: `AttributeList`, `BlockTitle`, update `TableBlock` (add attrs, title, hasBlankAfterFirstRow)
2. Parser: attribute list parser, block title parser, update `notBlockStart` guard
3. Parser: wire attribute list + title into `tableBlock` parser, track blank-after-first-row
4. ASG: new types (`HAlign`, `VAlign`, `ColumnSpec`), update `Table` with new fields
5. Bridge: `cols` parsing, header/footer detection with `%noheader` support, attribute extraction, title
6. DSL: update AST and ASG DSLs
7. Tests: write all custom TCK test cases
8. Visitor: update AST/ASG visitors for new node types
