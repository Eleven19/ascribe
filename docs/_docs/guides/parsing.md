---
title: Parser Guide
---

# Parser Guide

Ascribe's parser is built on [Parsley](https://github.com/j-mie6/parsley), a Scala parser combinator library. The parsing pipeline is split into three layers: lexer primitives, inline parsers, and block parsers.

## Entry Point

The top-level parser is `DocumentParser.document`:

```scala
import io.eleven19.ascribe.parser.DocumentParser

DocumentParser.document.parse("= Title\n\nHello world.\n")
```

This is also exposed via the public API `Ascribe.parse(source)`.

## Block Parsers

`BlockParser` (in `io.eleven19.ascribe.parser.BlockParser`) defines parsers for each block type:

### Headings

Heading levels 1--5 correspond to `=` through `=====`, followed by a space and the title text:

```asciidoc
= Document Title
== Chapter
=== Section
==== Subsection
===== Sub-subsection
```

The parser tries longer marker sequences first (`=====` before `====`) using `atomic` for clean backtracking.

### Paragraphs

A paragraph is one or more consecutive non-blank lines that do not start with a block prefix (heading markers, list markers, or delimiters). Each line is parsed as a list of inline elements; consecutive lines are concatenated.

### Unordered Lists

Items prefixed with `* `:

```asciidoc
* First item
* Second item
```

Parsed by `BlockParser.unorderedList` using `Parsley.some(unorderedItem)`.

### Ordered Lists

Items prefixed with `. `:

```asciidoc
. Step one
. Step two
```

### Delimited Blocks

Ascribe supports all standard AsciiDoc delimited block types. Each block type uses a distinct delimiter character repeated 4+ times:

| Block Type | Delimiter | AST Node | Content |
|-----------|-----------|----------|---------|
| Listing | `----` | `Listing` | Verbatim string |
| Literal | `....` | `Literal` | Verbatim string |
| Sidebar | `****` | `Sidebar` | Nested blocks |
| Example | `====` | `Example` | Nested blocks |
| Quote | `____` | `Quote` | Nested blocks |
| Open | `--` | `Open` | Nested blocks |
| Passthrough | `++++` | `Pass` | Verbatim string |
| Comment | `////` | `Comment` | Verbatim string |

Verbatim blocks capture their content as a raw `String` without inline parsing. Container blocks (Sidebar, Example, Quote, Open) parse their body as nested blocks, supporting headings, paragraphs, lists, and further nesting.

Delimiters support variable-length fences (e.g., `------` and `--------` are both valid listing delimiters). The closing delimiter must match the opening delimiter's character and length.

#### Source Blocks

A listing block with a `[source]` attribute becomes a source block with language highlighting:

```asciidoc
[source,scala]
----
def hello = println("Hello")
----
```

### Tables

Tables are delimited by `|===` and support three data formats:

- **PSV** (pipe-separated values, default) -- cells delimited by `|`
- **CSV** (comma-separated values) -- cells delimited by `,`, set via `[%format=csv]` or `[separator=,]`
- **DSV** (delimiter-separated values) -- cells delimited by `:`, set via `[%format=dsv]` or `[separator=:]`

```asciidoc
[cols="2,1,1"]
|===
| Name | Role | Status

| Alice
| Developer
| Active
|===
```

Table parsing is implemented within `BlockParser` and handles:

- **Column specs** (`cols` attribute) -- proportional widths, alignment, and default cell styles (e.g., `cols="3*,>1,^.^1e"`)
- **Cell specifiers** -- per-cell style, horizontal/vertical alignment, column span, row span, and duplication count (e.g., `2.3+^.>s|`)
- **Attribute lists** -- block attributes like `[cols=..., frame=..., grid=..., stripes=...]` and block title (`.Table Title`)
- **Header/footer rows** -- determined by the `%header`, `%footer` options or an implicit header row
- **Nested tables** -- inner tables use `!===` delimiters with `!` as the cell separator

Table parsing produces `Table` AST nodes. The bridge converts these to ASG `Table`, `TableRow`, and `TableCell` nodes.

## Inline Parsers

`InlineParser` (in `io.eleven19.ascribe.parser.InlineParser`) handles inline markup within headings, paragraphs, and list items:

| Syntax | Node | Description |
|--------|------|-------------|
| `**text**` | `Bold` | Unconstrained bold |
| `*text*` | `ConstrainedBold` | Constrained bold |
| `__text__` | `Italic` | Unconstrained italic |
| `` ``text`` `` | `Mono` | Unconstrained monospace |
| plain text | `Text` | Unformatted content |

Unconstrained variants (double delimiters) are tried before constrained (single delimiters) to avoid ambiguity. A lone markup character that does not open a valid span falls through to `unpairedMarkupInline` and is captured as `Text`.

## Section Restructuring

The parser initially produces a flat list of blocks. `DocumentParser.restructure` converts `Heading` nodes (level >= 2) into nested `Section` containers:

1. When a `Heading` at level N is encountered, all subsequent blocks are collected until the next heading of level <= N.
2. The collected blocks become the section's children (recursively restructured).
3. Level-1 headings (`=`) are document titles and are not restructured.
4. ASG section levels are offset by 1: `==` becomes section level 1, `===` becomes level 2, etc.

## Document Header and Attributes

A document header is a level-1 heading (`= Title`) optionally followed by attribute entries:

```asciidoc
= My Document
:author: Jane Doe
:version: 1.0
```

Attribute entries follow the pattern `:key: value` or `:key:` (empty value). They are stored as `(String, String)` pairs in `DocumentHeader.attributes`.

## Position Tracking

Every AST node carries a `Span(start: Position, end: Position)` recording its source location. Position tracking is achieved via Parsley's `pos` combinator and custom `PosParserBridge` traits that automatically capture positions around parsed content and pass them to node constructors.
