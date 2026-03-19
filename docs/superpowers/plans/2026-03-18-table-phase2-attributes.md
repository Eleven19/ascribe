# Table Phase 2: Attributes & Metadata — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add attribute lists, column specs, header/footer rows, titles, and display attributes to table support.

**Architecture:** Layered AST → Parser → Bridge → ASG. New `AttributeList` and `BlockTitle` AST nodes are general-purpose but wired only to tables in this phase. Column spec parsing (`cols`) happens in the bridge layer. The ASG `Table` gains `columns`, `header`, `footer`, `frame`, `grid`, `stripes` fields.

**Tech Stack:** Scala 3, Parsley (parser combinators), zio-blocks-schema (JSON codecs), Mill (build)

**Spec:** `docs/superpowers/specs/2026-03-18-table-phase2-attributes-design.md`

**Test command:** `./mill ascribe.tck-runner.test`

---

## File Map

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `ascribe/src/io/eleven19/ascribe/ast/Document.scala:83` | Add `AttributeList` (with opaque types companion), `BlockTitle`, update `TableBlock` |
| Modify | `ascribe/src/io/eleven19/ascribe/parser/BlockParser.scala` | Attribute list parser, block title parser, wire into `tableBlock` |
| Modify | `ascribe/src/io/eleven19/ascribe/parser/DocumentParser.scala:28` | Add `tableBlock` priority (it now needs title/attrs lookahead) |
| Create | `ascribe/bridge/src/io/eleven19/ascribe/bridge/ColsParser.scala` | `cols` attribute string → `Chunk[ColumnSpec]` |
| Create | `ascribe/asg/src/io/eleven19/ascribe/asg/ColumnSpec.scala` | `HAlign`, `VAlign`, `ColumnSpec` types |
| Modify | `ascribe/asg/src/io/eleven19/ascribe/asg/Node.scala:507-530` | Add fields to `Table` |
| Modify | `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala:51-57` | Header/footer detection, cols, title, attrs |
| Modify | `ascribe/src/io/eleven19/ascribe/ast/dsl.scala:46-49` | Update AST DSL |
| Modify | `ascribe/asg/src/io/eleven19/ascribe/asg/dsl.scala:61-71` | Update ASG DSL |
| Modify | `ascribe/src/io/eleven19/ascribe/ast/AstVisitor.scala:32-34,72-84` | Add `AttributeList`, `BlockTitle` visitor/children |
| Modify | `ascribe/asg/src/io/eleven19/ascribe/asg/AsgVisitor.scala:130-132` | Update Table children for header/footer |
| Modify | `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala:166-188` | Update `lastContentPos` for new AST nodes |
| Create | `ascribe/tck-runner/test/resources/tests/block/table/*-input.adoc` | 15 test input files |
| Create | `ascribe/tck-runner/test/resources/tests/block/table/*-output.json` | 15 test output files |

---

### Task 1: AST — `AttributeList` and `BlockTitle` types

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/ast/Document.scala:83-89`

**Important:** `AstNode` is a `sealed trait` (line 4), so ALL subtypes must be defined in `Document.scala`. The `AttributeList` case class, its companion with opaque types, and `BlockTitle` all go in this file.

- [ ] **Step 1: Add `AttributeList` companion (opaque types) and case class to `Document.scala`**

Add before the `TableBlock` definition (before line 83):

```scala
/** Opaque types for attribute list values — compile-time safety, zero runtime overhead. */
object AttributeList:
    opaque type AttributeName  = String
    opaque type AttributeValue = String
    opaque type OptionName     = String
    opaque type RoleName       = String

    object AttributeName:
        def apply(s: String): AttributeName       = s
        extension (n: AttributeName) def value: String = n

    object AttributeValue:
        def apply(s: String): AttributeValue       = s
        extension (v: AttributeValue) def value: String = v

    object OptionName:
        def apply(s: String): OptionName       = s
        extension (o: OptionName) def value: String = o

    object RoleName:
        def apply(s: String): RoleName       = s
        extension (r: RoleName) def value: String = r

    /** Merge two AttributeLists. Later named attrs override; options/roles accumulate. */
    def merge(a: AttributeList, b: AttributeList)(span: Span): AttributeList =
        AttributeList(
            positional = a.positional ++ b.positional,
            named = a.named ++ b.named,
            options = a.options ++ b.options,
            roles = a.roles ++ b.roles
        )(span)

/** A parsed attribute list: [key=value, %option, .role] */
case class AttributeList(
    positional: List[AttributeList.AttributeValue],
    named: Map[AttributeList.AttributeName, AttributeList.AttributeValue],
    options: List[AttributeList.OptionName],
    roles: List[AttributeList.RoleName]
)(val span: Span) extends AstNode derives CanEqual
```

- [ ] **Step 2: Add `BlockTitle` to `Document.scala` and update `TableBlock`**

Add `BlockTitle` after `AttributeList` (also in `Document.scala` since `AstNode` is sealed). Then update `TableBlock`:

```scala
/** A block title: .Title text */
case class BlockTitle(content: InlineContent)(val span: Span) extends AstNode derives CanEqual
```

Update `TableBlock` (line 83) from:
```scala
case class TableBlock(rows: List[TableRow], delimiter: String)(val span: Span) extends Block derives CanEqual
```
to:
```scala
case class TableBlock(
    rows: List[TableRow],
    delimiter: String,
    attributes: Option[AttributeList] = None,
    title: Option[BlockTitle] = None,
    hasBlankAfterFirstRow: Boolean = false
)(val span: Span) extends Block derives CanEqual
```

- [ ] **Step 3: Verify AST module compilation**

Run: `./mill ascribe.compile`
Expected: PASS (default values on `TableBlock` preserve backward compatibility within the AST module)

**Note:** The bridge module (`AstToAsg.scala`) will NOT compile until Task 6, because the `TableBlock` pattern match at line 51 needs updating. Only verify the `ascribe` module here, not the full project.

- [ ] **Step 4: Commit**

```bash
git add ascribe/src/io/eleven19/ascribe/ast/Document.scala
git commit -m "feat(ast): add AttributeList with opaque types and BlockTitle"
```

---

### Task 2: ASG — `ColumnSpec` types and `Table` updates

**Files:**
- Create: `ascribe/asg/src/io/eleven19/ascribe/asg/ColumnSpec.scala`
- Modify: `ascribe/asg/src/io/eleven19/ascribe/asg/Node.scala:507-530`

- [ ] **Step 1: Create `ColumnSpec.scala`**

```scala
package io.eleven19.ascribe.asg

import zio.blocks.schema.Schema

/** Horizontal alignment for a table column. */
enum HAlign derives Schema:
    case Left, Center, Right

/** Vertical alignment for a table column. */
enum VAlign derives Schema:
    case Top, Middle, Bottom

/** Column specification parsed from the cols attribute. */
case class ColumnSpec(
    width: Option[Int] = None,
    halign: Option[HAlign] = None,
    valign: Option[VAlign] = None
) derives Schema
```

**Important — enum serialization:** `derives Schema` on Scala 3 enums may produce capitalized names (`"Left"`) by default. The JSON output needs lowercase (`"left"`, `"center"`, `"right"`, `"top"`, `"middle"`, `"bottom"`). You may need a custom `NameMapper` on the codec or override `toString` on the enum cases. Verify during the TCK test phase (Task 9) and fix if needed — the `AsgCodecs` already uses `withCaseNameMapper` which may need to handle these enums.

- [ ] **Step 2: Update `Table` in `Node.scala`**

Replace the `Table` case class (lines 507-530) with:

```scala
@specStatus(
    SpecStatus.SpecDerived,
    "Table ASG structure inferred from AsciiDoc Language spec; no TCK test cases exist yet"
)
case class Table private (
    id: Option[String],
    title: Option[Chunk[Inline]],
    reftext: Option[Chunk[Inline]],
    metadata: Option[BlockMetadata],
    form: String,
    delimiter: String,
    columns: Option[Chunk[ColumnSpec]],
    header: Option[Chunk[Block]],
    rows: Chunk[Block],
    footer: Option[Chunk[Block]],
    frame: Option[String],
    grid: Option[String],
    stripes: Option[String],
    location: Location,
    @Modifier.rename("type") nodeType: String
) extends Block derives Schema

object Table:

    def apply(
        id: Option[String] = None,
        title: Option[Chunk[Inline]] = None,
        reftext: Option[Chunk[Inline]] = None,
        metadata: Option[BlockMetadata] = None,
        form: String = "delimited",
        delimiter: String = "|===",
        columns: Option[Chunk[ColumnSpec]] = None,
        header: Option[Chunk[Block]] = None,
        rows: Chunk[Block],
        footer: Option[Chunk[Block]] = None,
        frame: Option[String] = None,
        grid: Option[String] = None,
        stripes: Option[String] = None,
        location: Location
    ): Table = new Table(id, title, reftext, metadata, form, delimiter, columns, header, rows, footer, frame, grid, stripes, location, "block")
```

- [ ] **Step 3: Verify compilation**

Run: `./mill ascribe.asg.compile`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add ascribe/asg/src/io/eleven19/ascribe/asg/ColumnSpec.scala ascribe/asg/src/io/eleven19/ascribe/asg/Node.scala
git commit -m "feat(asg): add ColumnSpec types and extend Table with columns/header/footer/frame/grid/stripes"
```

---

### Task 3: Parser — attribute list and block title parsers

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/parser/BlockParser.scala`

- [ ] **Step 1: Add attribute list parser**

Add after the `// Tables` comment (line 139) in `BlockParser.scala`, before the existing table parsers:

```scala
    // -----------------------------------------------------------------------
    // Attribute lists and block titles
    // -----------------------------------------------------------------------

    import io.eleven19.ascribe.ast.{AttributeList, BlockTitle}
    import io.eleven19.ascribe.ast.AttributeList.{AttributeName, AttributeValue, OptionName, RoleName}

    /** Parses a quoted string value: "..." */
    private val quotedValue: Parsley[String] =
        char('"') *> stringOfSome(satisfy(c => c != '"' && c != '\n' && c != '\r')) <* char('"')

    /** Parses an unquoted attribute value (no commas, no brackets, no quotes). */
    private val unquotedValue: Parsley[String] =
        stringOfSome(satisfy(c => c != ',' && c != ']' && c != '"' && c != '\n' && c != '\r' && c != '%' && c != '#' && c != '.'))

    /** ADT for parsed attribute entries — cleaner than nested Either. */
    private enum AttrEntry:
        case Named(key: AttributeName, value: AttributeValue)
        case Opt(name: OptionName)
        case Role(name: RoleName)
        case Positional(value: AttributeValue)

    private val shorthandChars = Set(',', ']', '%', '#', '.', '\n', '\r')

    /** Parses a single attribute entry within [...]:
      *   - %option
      *   - #id  (produces Named("id", value))
      *   - .role
      *   - key=value or key="value"
      *   - positional value
      */
    private val attrEntry: Parsley[AttrEntry] =
        (char('%') *> stringOfSome(satisfy(c => !shorthandChars(c))))
            .map(s => AttrEntry.Opt(OptionName(s.trim))) |
        (char('#') *> stringOfSome(satisfy(c => !shorthandChars(c))))
            .map(s => AttrEntry.Named(AttributeName("id"), AttributeValue(s.trim))) |
        (char('.') *> notFollowedBy(char('.')) *> stringOfSome(satisfy(c => !shorthandChars(c))))
            .map(s => AttrEntry.Role(RoleName(s.trim))) |
        atomic((unquotedValue <* char('=')) <~> (quotedValue | unquotedValue))
            .map { case (k, v) => AttrEntry.Named(AttributeName(k.trim), AttributeValue(v.trim)) } |
        (quotedValue | unquotedValue)
            .map(s => AttrEntry.Positional(AttributeValue(s.trim)))

- [ ] **Step 2: Add the `attributeList` combinator**

```scala
    /** Parses a complete attribute list line: [...] followed by EOL. */
    val attributeListLine: Parsley[AttributeList] =
        (pos <~> (char('[') *> sepEndBy(attrEntry, char(',') *> option(char(' ')).void) <* char(']') <* eolOrEof) <~> pos)
            .map { case ((s, entries), e) =>
                val positional = entries.collect { case AttrEntry.Positional(v) => v }.toList
                val named = entries.collect { case AttrEntry.Named(k, v) => (k, v) }.toMap
                val options = entries.collect { case AttrEntry.Opt(o) => o }.toList
                val roles = entries.collect { case AttrEntry.Role(r) => r }.toList
                AttributeList(positional, named, options, roles)(mkSpan(s, e))
            }
            .label("attribute list")

    /** Parses zero or more attribute list lines and merges them. */
    val attributeLists: Parsley[Option[AttributeList]] =
        many(attributeListLine).map {
            case Nil => None
            case first :: rest =>
                Some(rest.foldLeft(first)((acc, al) =>
                    AttributeList.merge(acc, al)(Span(acc.span.start, al.span.end))
                ))
        }
```

- [ ] **Step 3: Add the block title parser**

```scala
    /** Parses a block title line: .TitleText (dot + non-whitespace, not a delimiter like ....) */
    val blockTitle: Parsley[BlockTitle] =
        (pos <~> (char('.') *> notFollowedBy(char('.')) *> notFollowedBy(char(' ')) *> lineContent) <~> pos <* eolOrEof)
            .map { case ((s, content), e) => BlockTitle(content)(mkSpan(s, e)) }
            .label("block title")
```

- [ ] **Step 4: Verify compilation**

Run: `./mill ascribe.compile`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ascribe/src/io/eleven19/ascribe/parser/BlockParser.scala
git commit -m "feat(parser): add attribute list and block title parsers"
```

---

### Task 4: Parser — wire attribute list and title into `tableBlock`

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/parser/BlockParser.scala:177-203`
- Modify: `ascribe/src/io/eleven19/ascribe/parser/DocumentParser.scala:28`

- [ ] **Step 1: Update `tableBlock` parser to consume title and attributes**

Replace the `tableBlock` parser (lines 177-187) with:

```scala
    /** Parses a table block: optional .title, optional [attrs], |=== open, rows, |=== close.
      *
      * Tracks whether a blank line appears after the first row for implicit header detection.
      */
    val tableBlock: Parsley[Block] =
        // atomic wraps title+attrs+delimiter so if |=== is not found, the parser
        // backtracks and doesn't consume .Title or [attrs] for non-table blocks
        (pos <~> atomic(option(blockTitle) <~> attributeLists <~>
            (string("|===") <* eolOrEof)) *>
            option(some(blankLine)).void *>
            tableRowsWithBlankTracking <~> pos <* eolOrEof)
            .map { case ((((s, title), attrs), (rows, hasBlankAfterFirst)), e) =>
                TableBlock(rows, "|===", attrs, title, hasBlankAfterFirst)(mkSpan(s, e))
            }
            .label("table block")

    /** Parses table rows, tracking whether there's a blank line after the first row. */
    private val tableRowsWithBlankTracking: Parsley[(List[TableRow], Boolean)] =
        option(tableRow).flatMap {
            case None =>
                // No rows before closing delimiter
                (atomic(string("|==="))).map(_ => (Nil, false))
            case Some(firstRow) =>
                val blankThenMore = some(blankLine).void *>
                    manyTill(
                        tableRow <* option(some(blankLine)).void,
                        atomic(string("|==="))
                    ).map(rest => (firstRow :: rest, true))
                val noBlankMore = manyTill(
                    tableRow <* option(some(blankLine)).void,
                    atomic(string("|==="))
                ).map(rest => (firstRow :: rest, false))
                blankThenMore | noBlankMore |
                    atomic(string("|===")).map(_ => (List(firstRow), false))
        }
```

- [ ] **Step 2: Update `notBlockStart` to exclude attribute lists and block titles**

Replace `notBlockStart` (lines 197-203) with:

```scala
    private val notBlockStart: Parsley[Unit] =
        notFollowedBy(headingLevel *> char(' ')) *>
            notFollowedBy(char('*') *> char(' ')) *>
            notFollowedBy(char('.') *> char(' ')) *>
            notFollowedBy(char('.') *> satisfy(c => c != '.' && c != ' ' && c != '\n' && c != '\r')) *>  // block title
            notFollowedBy(char('[') *> satisfy(c => c != '\n' && c != '\r')) *>  // attribute list
            notFollowedBy(string("----")) *>
            notFollowedBy(string("****")) *>
            notFollowedBy(string("|==="))
```

- [ ] **Step 3: Update `DocumentParser` block priority**

In `DocumentParser.scala` line 28, the `block` parser already has `tableBlock` in the right position. However, since `tableBlock` now consumes preceding title/attrs, it needs to be tried before `paragraph`. The current order `listingBlock | sidebarBlock | tableBlock | heading | ...` is correct — `tableBlock` already comes before `paragraph`.

Verify this order is preserved. No change needed if `tableBlock` stays before `paragraph`.

- [ ] **Step 4: Verify compilation and run existing tests**

Run: `./mill ascribe.tck-runner.test`
Expected: All 32 existing scenarios PASS (backward compatible due to default values)

- [ ] **Step 5: Commit**

```bash
git add ascribe/src/io/eleven19/ascribe/parser/BlockParser.scala ascribe/src/io/eleven19/ascribe/parser/DocumentParser.scala
git commit -m "feat(parser): wire attribute lists and block titles into tableBlock with blank-line tracking"
```

---

### Task 5: Bridge — `ColsParser` for column specifications

**Files:**
- Create: `ascribe/bridge/src/io/eleven19/ascribe/bridge/ColsParser.scala`

- [ ] **Step 1: Write a failing test for cols parsing**

Create `ascribe/bridge/test/src/io/eleven19/ascribe/bridge/ColsParserSpec.scala`:

```scala
package io.eleven19.ascribe.bridge

import zio.blocks.chunk.Chunk
import io.eleven19.ascribe.asg.{ColumnSpec, HAlign, VAlign}

class ColsParserSpec extends munit.FunSuite:

    test("parse equal columns: 3*") {
        val result = ColsParser.parse("3*")
        assertEquals(result, Chunk(ColumnSpec(), ColumnSpec(), ColumnSpec()))
    }

    test("parse explicit widths: 1,2,3") {
        val result = ColsParser.parse("1,2,3")
        assertEquals(result, Chunk(
            ColumnSpec(width = Some(1)),
            ColumnSpec(width = Some(2)),
            ColumnSpec(width = Some(3))
        ))
    }

    test("parse horizontal alignment: <,^,>") {
        val result = ColsParser.parse("<,^,>")
        assertEquals(result, Chunk(
            ColumnSpec(halign = Some(HAlign.Left)),
            ColumnSpec(halign = Some(HAlign.Center)),
            ColumnSpec(halign = Some(HAlign.Right))
        ))
    }

    test("parse vertical alignment: .<,.^,.>") {
        val result = ColsParser.parse(".<,.^,.>")
        assertEquals(result, Chunk(
            ColumnSpec(valign = Some(VAlign.Top)),
            ColumnSpec(valign = Some(VAlign.Middle)),
            ColumnSpec(valign = Some(VAlign.Bottom))
        ))
    }

    test("parse mixed alignment + width: <1,^2,>3") {
        val result = ColsParser.parse("<1,^2,>3")
        assertEquals(result, Chunk(
            ColumnSpec(width = Some(1), halign = Some(HAlign.Left)),
            ColumnSpec(width = Some(2), halign = Some(HAlign.Center)),
            ColumnSpec(width = Some(3), halign = Some(HAlign.Right))
        ))
    }

    test("parse multiplier with alignment: 5,3*>") {
        val result = ColsParser.parse("5,3*>")
        assertEquals(result, Chunk(
            ColumnSpec(width = Some(5)),
            ColumnSpec(halign = Some(HAlign.Right)),
            ColumnSpec(halign = Some(HAlign.Right)),
            ColumnSpec(halign = Some(HAlign.Right))
        ))
    }

    test("parse combined h+v align: >.^2") {
        val result = ColsParser.parse(">.^2")
        assertEquals(result, Chunk(
            ColumnSpec(width = Some(2), halign = Some(HAlign.Right), valign = Some(VAlign.Middle))
        ))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mill ascribe.bridge.test`
Expected: FAIL — `ColsParser` not found

- [ ] **Step 3: Implement `ColsParser`**

Create `ascribe/bridge/src/io/eleven19/ascribe/bridge/ColsParser.scala`:

```scala
package io.eleven19.ascribe.bridge

import zio.blocks.chunk.Chunk
import io.eleven19.ascribe.asg.{ColumnSpec, HAlign, VAlign}

/** Parses AsciiDoc `cols` attribute values into column specifications. */
object ColsParser:

    def parse(cols: String): Chunk[ColumnSpec] =
        val entries = cols.split(",").map(_.trim).toList
        Chunk.from(entries.flatMap(parseEntry))

    private def parseEntry(entry: String): List[ColumnSpec] =
        // Check for multiplier: <n>*<rest>
        val multiplierPattern = """^(\d+)\*(.*)$""".r
        entry match
            case multiplierPattern(nStr, rest) =>
                val n = nStr.toInt
                val spec = parseSpec(rest)
                List.fill(n)(spec)
            case _ =>
                List(parseSpec(entry))

    private def parseSpec(s: String): ColumnSpec =
        var remaining = s
        var halign: Option[HAlign] = None
        var valign: Option[VAlign] = None
        var width: Option[Int] = None

        // Parse horizontal alignment prefix
        if remaining.startsWith("<") then
            halign = Some(HAlign.Left); remaining = remaining.drop(1)
        else if remaining.startsWith("^") then
            halign = Some(HAlign.Center); remaining = remaining.drop(1)
        else if remaining.startsWith(">") then
            halign = Some(HAlign.Right); remaining = remaining.drop(1)

        // Parse vertical alignment prefix
        if remaining.startsWith(".<") then
            valign = Some(VAlign.Top); remaining = remaining.drop(2)
        else if remaining.startsWith(".^") then
            valign = Some(VAlign.Middle); remaining = remaining.drop(2)
        else if remaining.startsWith(".>") then
            valign = Some(VAlign.Bottom); remaining = remaining.drop(2)

        // Parse width (remaining digits)
        if remaining.nonEmpty && remaining.forall(_.isDigit) then
            width = Some(remaining.toInt)

        ColumnSpec(width, halign, valign)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mill ascribe.bridge.test`
Expected: All `ColsParserSpec` tests PASS

- [ ] **Step 5: Commit**

```bash
git add ascribe/bridge/src/io/eleven19/ascribe/bridge/ColsParser.scala ascribe/bridge/test/src/io/eleven19/ascribe/bridge/ColsParserSpec.scala
git commit -m "feat(bridge): add ColsParser for cols attribute string parsing"
```

---

### Task 6: Bridge — header/footer detection, title, and attribute extraction

**Files:**
- Modify: `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala:51-57,105-115,166-188`

- [ ] **Step 1: Update `convertBlock` for the `TableBlock` case**

Replace lines 51-57 in `AstToAsg.scala`:

```scala
        case tb @ ast.TableBlock(rows, delimiter, attrsOpt, titleOpt, hasBlankAfterFirst) =>
            val options = attrsOpt.toList.flatMap(_.options).map(_.value)
            val named = attrsOpt.map(_.named.map((k, v) => (k.value, v.value))).getOrElse(Map.empty)

            // Parse columns from cols attribute
            val columns = named.get("cols").map(ColsParser.parse)

            // Header detection: %noheader suppresses, %header forces, blank-after-first implies
            val hasHeader = if options.contains("noheader") then false
                else if options.contains("header") then true
                else hasBlankAfterFirst

            // Footer detection: %footer promotes last row
            val hasFooter = options.contains("footer")

            // Split rows into header, body, footer
            val allRows = rows.map(convertTableRow)
            val (headerRow, bodyStart) = if hasHeader && allRows.nonEmpty then
                (Some(Chunk.from(allRows.head.cells.toList.map(c => c: asg.Block))), allRows.tail)
            else (None, allRows)
            val (bodyRows, footerRow) = if hasFooter && bodyStart.nonEmpty then
                (bodyStart.init, Some(Chunk.from(bodyStart.last.cells.toList.map(c => c: asg.Block))))
            else (bodyStart, None)

            // Title
            val title = titleOpt.map(bt => Chunk.from(bt.content.map(convertInline)))

            // Display attributes
            val validFrames = Set("all", "ends", "sides", "none")
            val validGrids = Set("all", "cols", "rows", "none")
            val validStripes = Set("none", "even", "odd", "hover", "all")
            val frame = named.get("frame").filter(validFrames.contains)
            val grid = named.get("grid").filter(validGrids.contains)
            val stripes = named.get("stripes").filter(validStripes.contains)

            // Metadata
            val metadata = attrsOpt.map(al => asg.BlockMetadata(
                attributes = al.named.map((k, v) => (k.value, v.value)),
                options = Chunk.from(al.options.map(_.value)),
                roles = Chunk.from(al.roles.map(_.value))
            ))

            asg.Table(
                title = title,
                metadata = metadata,
                form = "delimited",
                delimiter = delimiter,
                columns = columns,
                header = headerRow,
                rows = Chunk.from(bodyRows.map(r => r: asg.Block)),
                footer = footerRow,
                frame = frame,
                grid = grid,
                stripes = stripes,
                location = inclusiveLocation(block.span)
            )
```

**Note:** The `convertTableRow` helper now needs to return `asg.TableRow` (not just `asg.Block`) so we can access `.cells` for header/footer extraction. It already returns `asg.TableRow` — just need to keep the return type specific.

- [ ] **Step 2: Update `lastContentPos` for new AST nodes**

Add cases for `AttributeList` and `BlockTitle` in `lastContentPos` (around line 182):

```scala
        case al: ast.AttributeList => al.span.end
        case bt: ast.BlockTitle    => bt.content.lastOption.map(lastContentPos).getOrElse(bt.span.end)
```

- [ ] **Step 3: Verify compilation**

Run: `./mill ascribe.bridge.compile`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala
git commit -m "feat(bridge): add header/footer detection, cols, title, and display attribute extraction"
```

---

### Task 7: DSL — update AST and ASG DSLs

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/ast/dsl.scala:46-49`
- Modify: `ascribe/asg/src/io/eleven19/ascribe/asg/dsl.scala:61-71`

- [ ] **Step 1: Update AST DSL**

Replace lines 46-49 in `ast/dsl.scala`:

```scala
    // --- Attribute lists and block titles ---
    def attributeList(
        named: (String, String)*
    ): AttributeList = AttributeList(
        positional = Nil,
        named = named.map((k, v) => (AttributeList.AttributeName(k), AttributeList.AttributeValue(v))).toMap,
        options = Nil,
        roles = Nil
    )(u)

    def attributeList(
        options: List[String],
        named: Map[String, String] = Map.empty
    ): AttributeList = AttributeList(
        positional = Nil,
        named = named.map((k, v) => (AttributeList.AttributeName(k), AttributeList.AttributeValue(v))),
        options = options.map(AttributeList.OptionName(_)),
        roles = Nil
    )(u)

    def blockTitle(inlines: Inline*): BlockTitle = BlockTitle(inlines.toList)(u)

    // --- Tables ---
    def tableBlock(rows: TableRow*): TableBlock = TableBlock(rows.toList, "|===")(u)

    def tableBlock(attrs: AttributeList, rows: TableRow*): TableBlock =
        TableBlock(rows.toList, "|===", Some(attrs))(u)

    def tableBlock(title: BlockTitle, attrs: AttributeList, rows: TableRow*): TableBlock =
        TableBlock(rows.toList, "|===", Some(attrs), Some(title))(u)

    def tableRow(cells: TableCell*): TableRow   = TableRow(cells.toList)(u)
    def tableCell(inlines: Inline*): TableCell  = TableCell(inlines.toList)(u)
```

- [ ] **Step 2: Update ASG DSL**

Replace lines 61-71 in `asg/dsl.scala`:

```scala
    // --- Tables ---
    def table(
        columns: Option[Chunk[ColumnSpec]] = None,
        header: Option[Chunk[Block]] = None,
        footer: Option[Chunk[Block]] = None,
        frame: Option[String] = None,
        grid: Option[String] = None,
        stripes: Option[String] = None,
        rows: Block*
    )(using l: Location): Table =
        Table(
            columns = columns,
            header = header,
            rows = Chunk.from(rows),
            footer = footer,
            frame = frame,
            grid = grid,
            stripes = stripes,
            location = l
        )

    // Keep backward-compatible overload
    def table(rows: Block*)(using l: Location): Table =
        Table(rows = Chunk.from(rows), location = l)

    def tableRow(cells: Block*)(using l: Location): TableRow =
        TableRow(cells = Chunk.from(cells), location = l)
    def tr(cells: Block*)(using l: Location): TableRow = tableRow(cells*)

    def tableCell(inlines: Inline*)(using l: Location): TableCell =
        TableCell(inlines = Chunk.from(inlines), location = l)
    def tc(inlines: Inline*)(using l: Location): TableCell = tableCell(inlines*)
```

- [ ] **Step 3: Verify compilation**

Run: `./mill ascribe.compile && ./mill ascribe.asg.compile`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add ascribe/src/io/eleven19/ascribe/ast/dsl.scala ascribe/asg/src/io/eleven19/ascribe/asg/dsl.scala
git commit -m "feat(dsl): update AST and ASG DSLs for table attributes"
```

---

### Task 8: Visitors — update for new AST nodes and ASG Table fields

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/ast/AstVisitor.scala:32-34,72-84`
- Modify: `ascribe/asg/src/io/eleven19/ascribe/asg/AsgVisitor.scala:130-132`

- [ ] **Step 1: Update AST visitor**

Add visitor methods (after line 34):
```scala
    def visitAttributeList(node: AttributeList): A = visitNode(node)
    def visitBlockTitle(node: BlockTitle): A        = visitNode(node)
```

Add dispatch cases (after line 63):
```scala
        case n: AttributeList => visitor.visitAttributeList(n)
        case n: BlockTitle    => visitor.visitBlockTitle(n)
```

Update `children` for `TableBlock` (line 82):
```scala
        case tb: TableBlock      => tb.title.toList ++ tb.attributes.toList ++ tb.rows
```

Add children cases:
```scala
        case al: AttributeList   => Nil  // leaf node
        case bt: BlockTitle      => bt.content
```

- [ ] **Step 2: Update ASG visitor `children` for Table**

Update the Table children case (around line 130) to include header and footer:

```scala
        case t: Table      => t.header.toList.flatMap(_.toList) ++ t.rows.toList ++ t.footer.toList.flatMap(_.toList)
```

- [ ] **Step 3: Verify compilation and run tests**

Run: `./mill ascribe.tck-runner.test`
Expected: All 32 existing scenarios PASS

- [ ] **Step 4: Commit**

```bash
git add ascribe/src/io/eleven19/ascribe/ast/AstVisitor.scala ascribe/asg/src/io/eleven19/ascribe/asg/AsgVisitor.scala
git commit -m "feat(visitor): update AST/ASG visitors for AttributeList, BlockTitle, and Table header/footer"
```

---

### Task 9: TCK tests — cols attribute tests

**Files:**
- Create: `ascribe/tck-runner/test/resources/tests/block/table/cols-equal-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/cols-equal-output.json`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/cols-widths-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/cols-widths-output.json`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/cols-alignment-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/cols-alignment-output.json`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/cols-mixed-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/cols-mixed-output.json`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/cols-valign-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/cols-valign-output.json`

- [ ] **Step 1: Write `cols-equal` test pair**

`cols-equal-input.adoc`:
```asciidoc
[cols="3*"]
|===
| A | B | C
| D | E | F
|===
```

`cols-equal-output.json`: Write the expected JSON document with `"columns"` array containing three `ColumnSpec` entries with no width/alignment, and two body rows of three cells each. Use the `basic-2x2-output.json` as a template for the location/structure format.

- [ ] **Step 2: Write `cols-widths` test pair**

`cols-widths-input.adoc`:
```asciidoc
[cols="1,2,3"]
|===
| A | B | C
| D | E | F
|===
```

- [ ] **Step 3: Write `cols-alignment` test pair**

`cols-alignment-input.adoc`:
```asciidoc
[cols="<,^,>"]
|===
| Left | Center | Right
|===
```

- [ ] **Step 4: Write `cols-mixed` test pair**

`cols-mixed-input.adoc`:
```asciidoc
[cols="<1,^2,>3"]
|===
| Left | Center | Right
|===
```

- [ ] **Step 5: Write `cols-valign` test pair**

`cols-valign-input.adoc`:
```asciidoc
[cols=".<,.^,.>"]
|===
| Top | Middle | Bottom
|===
```

Expected: 3 columns with `valign` only.

- [ ] **Step 6: Run tests to verify they fail (parser doesn't consume attrs yet or JSON doesn't match)**

Run: `./mill ascribe.tck-runner.test`
Expected: New tests FAIL, existing tests PASS

- [ ] **Step 7: Fix any issues until all cols tests pass**

Iterate on the expected JSON and implementation until the parser + bridge produce matching output.

- [ ] **Step 8: Commit**

```bash
git add ascribe/tck-runner/test/resources/tests/block/table/cols-*
git commit -m "test: add custom TCK tests for cols attribute (equal, widths, alignment, valign, mixed)"
```

---

### Task 10: TCK tests — title, header, footer

**Files:**
- Create: `ascribe/tck-runner/test/resources/tests/block/table/table-title-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/table-title-output.json`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/header-implicit-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/header-implicit-output.json`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/header-explicit-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/header-explicit-output.json`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/header-noheader-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/header-noheader-output.json`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/footer-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/footer-output.json`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/header-footer-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/header-footer-output.json`

- [ ] **Step 1: Write `table-title` test pair**

`table-title-input.adoc`:
```asciidoc
.My Table
|===
| A | B
| C | D
|===
```

Expected output has `"title": [{"name": "text", "value": "My Table", ...}]` on the table node.

- [ ] **Step 2: Write `header-implicit` test pair**

`header-implicit-input.adoc`:
```asciidoc
|===
| Header 1 | Header 2

| Cell 1 | Cell 2
|===
```

Expected output has `"header"` field with two cells, and `"rows"` with one body row.

- [ ] **Step 3: Write `header-explicit` test pair**

`header-explicit-input.adoc`:
```asciidoc
[%header]
|===
| Header 1 | Header 2
| Cell 1 | Cell 2
|===
```

- [ ] **Step 4: Write `header-noheader` test pair**

`header-noheader-input.adoc`:
```asciidoc
[%noheader]
|===
| Row 1 | Row 1

| Row 2 | Row 2
|===
```

Expected: No `header` field despite blank line after first row. Two body rows.

- [ ] **Step 5: Write `footer` test pair**

`footer-input.adoc`:
```asciidoc
[%footer]
|===
| Body 1 | Body 2
| Footer 1 | Footer 2
|===
```

Expected: `"footer"` field with last row cells, `"rows"` with one body row.

- [ ] **Step 6: Write `header-footer` test pair**

`header-footer-input.adoc`:
```asciidoc
[%header%footer]
|===
| Header 1 | Header 2
| Body 1 | Body 2
| Footer 1 | Footer 2
|===
```

- [ ] **Step 7: Update existing `header-row` test for implicit header detection**

The existing `header-row-input.adoc` has a blank line after the first row, which now triggers implicit header detection. Update `header-row-output.json` to reflect the first row being promoted to `header` and removed from `rows`.

- [ ] **Step 8: Run tests, iterate until passing**

Run: `./mill ascribe.tck-runner.test`

- [ ] **Step 9: Commit**

```bash
git add ascribe/tck-runner/test/resources/tests/block/table/table-title-* ascribe/tck-runner/test/resources/tests/block/table/header-* ascribe/tck-runner/test/resources/tests/block/table/footer-*
git commit -m "test: add custom TCK tests for table title, header/footer detection"
```

---

### Task 11: TCK tests — display attributes and integration

**Files:**
- Create: `ascribe/tck-runner/test/resources/tests/block/table/frame-grid-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/frame-grid-output.json`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/stripes-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/stripes-output.json`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/full-attrs-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/full-attrs-output.json`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/cols-multiplier-align-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/cols-multiplier-align-output.json`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/stacked-attrs-input.adoc`
- Create: `ascribe/tck-runner/test/resources/tests/block/table/stacked-attrs-output.json`

- [ ] **Step 1: Write `frame-grid` test pair**

`frame-grid-input.adoc`:
```asciidoc
[frame=ends,grid=rows]
|===
| A | B
| C | D
|===
```

Expected: `"frame": "ends"`, `"grid": "rows"` on the table node.

- [ ] **Step 2: Write `stripes` test pair**

`stripes-input.adoc`:
```asciidoc
[stripes=even]
|===
| A | B
| C | D
|===
```

- [ ] **Step 3: Write `cols-multiplier-align` test pair**

`cols-multiplier-align-input.adoc`:
```asciidoc
[cols="5,3*>"]
|===
| A | B | C | D
|===
```

Expected: 4 columns — first with width 5, next three with halign "right".

- [ ] **Step 4: Write `stacked-attrs` test pair**

Tests that multiple `[...]` lines are merged correctly:

`stacked-attrs-input.adoc`:
```asciidoc
[%header]
[cols="1,2"]
|===
| H1 | H2
| A | B
|===
```

Expected: `%header` option from first line + `cols` from second line both present on the table.

- [ ] **Step 5: Write `full-attrs` integration test pair**

`full-attrs-input.adoc`:
```asciidoc
.Complete Table
[%header%footer,cols="<1,^2,>3",frame=ends,grid=rows,stripes=even]
|===
| Left | Center | Right
| A | B | C
| F1 | F2 | F3
|===
```

Expected: All fields present — title, columns, header, footer, rows, frame, grid, stripes.

- [ ] **Step 6: Run all tests, iterate until passing**

Run: `./mill ascribe.tck-runner.test`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add ascribe/tck-runner/test/resources/tests/block/table/frame-grid-* ascribe/tck-runner/test/resources/tests/block/table/stripes-* ascribe/tck-runner/test/resources/tests/block/table/cols-multiplier-align-* ascribe/tck-runner/test/resources/tests/block/table/stacked-attrs-* ascribe/tck-runner/test/resources/tests/block/table/full-attrs-*
git commit -m "test: add custom TCK tests for frame/grid/stripes, stacked attrs, and full integration"
```

---

### Task 12: Final verification and cleanup

- [ ] **Step 1: Run full test suite one more time**

Run: `./mill ascribe.tck-runner.test`
Expected: All scenarios PASS

- [ ] **Step 2: Run compilation for all modules**

Run: `./mill __.compile`
Expected: PASS

- [ ] **Step 3: Check for any compilation warnings**

Review output for deprecation warnings or unused imports. Fix if any.

- [ ] **Step 4: Final commit if any cleanup was needed**

```bash
git add -u
git commit -m "chore: Phase 2 table cleanup"
```
