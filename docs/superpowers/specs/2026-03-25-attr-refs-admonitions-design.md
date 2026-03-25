# Design: Attribute References and Admonition Blocks

**Date:** 2026-03-25
**Issues:** ascribe-a4e (attribute references), ascribe-ch0 (admonitions)
**Branch:** feat/adoc-spec-gaps

---

## Overview

Two independent features, both following the existing CST → AST → ASG pipeline pattern. Each adds new parsing support, a lowering transformation, and surface area in the AST and/or bridge.

---

## Feature 1: Attribute References and Substitution

### Goal

Implement document attribute references — the `{attr-name}` macro substitution system. Attribute values defined in the document header or body are interpolated inline wherever a reference appears.

### Scope

1. `{attribute-name}` inline references in paragraph/inline content
2. Attribute entry unset form: `:!name:` (removes the attribute from scope)
3. Body-level attribute entries that affect subsequent content (not just header)
4. Built-in special attributes: `{empty}`, `{nbsp}`, `{zwsp}`, `{sp}`

Out of scope for this issue: soft-set form `:name!:` (no CLI yet), attribute substitution inside attribute values (recursive substitution).

### Architecture

Four-layer change across: lexer → CST → inline/block parsers → lowering.

#### Layer 1 — Lexer (`AsciiDocLexer.scala`)

Add `{` and `}` to the set of characters excluded from `isContentChar`. This prevents `{` from being greedily consumed as plain text before the inline parser has a chance to recognize it as an attribute reference opener.

Add `{` to `unpairedMarkupChar` so an unmatched `{` falls back gracefully to `CstText("{")`.

#### Layer 2 — CST Nodes (`CstNodes.scala`)

New inline node:
```scala
case class CstAttributeRef(name: String)(val span: Span) extends CstInline derives CanEqual
```

Extended attribute entry:
```scala
case class CstAttributeEntry(name: String, value: String, unset: Boolean)(val span: Span)
    extends CstBlock derives CanEqual
```
`unset = true` when the form `:!name:` is used (value is always `""` in this case).

#### Layer 3 — Inline Parser (`InlineParser.scala`)

New parser `attrRefInline`:
- Matches `{` followed by one or more `[a-zA-Z0-9_-]` chars followed by `}`
- Produces `CstAttributeRef(name)`
- Falls back: unmatched `{` → `CstText("{")` via `unpairedMarkupInline`

Priority in `inlineElement`: `attrRefInline` inserted after `monoSpan` and before `plainTextInline`.

#### Layer 4 — Block Parser (`BlockParser.scala`)

`attributeEntryBlock` updated to recognise `:!name:` form:
- If the name starts with `!`, strip the `!` prefix and set `unset = true`
- Otherwise `unset = false` as before

#### Layer 5 — Lowering (`CstLowering.scala`)

`toAst` builds an attribute map before processing body blocks:

```
attributeMap = builtIns ++ headerAttributes
```

Built-in initial values:
| Reference | Value |
|-----------|-------|
| `{empty}` | `""` |
| `{sp}`    | `" "` |
| `{nbsp}`  | `"\u00A0"` |
| `{zwsp}`  | `"\u200B"` |

Body processing (sequential, not parallel):
- `CstAttributeEntry(name, value, unset=false)` → `attributeMap += name -> value`; no AST node emitted
- `CstAttributeEntry(name, _, unset=true)` → `attributeMap -= name`; no AST node emitted

`lowerInline` gains a new case:
```scala
case CstAttributeRef(name) =>
  Text(attributeMap.getOrElse(name, s"{$name}"))(inline.span)
```
Unresolved references pass through as literal `{name}` text — standard AsciiDoc behaviour.

Because resolution happens in lowering, the AST has no `AttributeRef` node. All attribute references lower to `ast.Text`.

### Data flow

```
":version: 1.0"  →  CstAttributeEntry("version","1.0",false)  →  map["version"]="1.0"
"{version}"      →  CstAttributeRef("version")                →  Text("1.0")
":!version:"     →  CstAttributeEntry("version","",true)      →  map.remove("version")
"{version}"      →  CstAttributeRef("version")                →  Text("{version}")
```

### No AST/ASG changes

The AST gains no new inline node. The ASG layer is untouched. No bridge or renderer changes needed.

---

## Feature 2: Admonition Blocks

### Goal

Support both syntactic forms of admonitions:
1. **Paragraph form** — `NOTE: text on same line`
2. **Delimited block form** — `[NOTE]\n====\ncontent\n====`

All five types: `NOTE`, `TIP`, `IMPORTANT`, `CAUTION`, `WARNING`.

### Scope

Delimited block form already flows end-to-end: `[NOTE]\n====` → `ast.Example` with `positional=["NOTE"]` → bridge detects admonition style → `asg.Admonition`. No changes needed there.

This feature adds the **paragraph form** only (new parsing + AST node + bridge + renderer handling).

### Architecture

#### Layer 1 — CST Nodes (`CstNodes.scala`)

New block node:
```scala
case class CstAdmonitionParagraph(kind: String, content: List[CstInline])(val span: Span)
    extends CstBlock derives CanEqual
```
`kind` is the raw uppercase label string (e.g. `"NOTE"`).

#### Layer 2 — Block Parser (`BlockParser.scala`)

New parser `admonitionParagraphBlock`:
- Matches one of the five uppercase labels followed by `: ` (colon + space)
- Parses remaining line as inline content
- Produces `CstAdmonitionParagraph(kind, content)`

`notCstBlockStart` gains a lookahead to reject admonition paragraph starters (preventing them from being consumed as ordinary paragraphs).

`block` combinator: `admonitionParagraphBlock` inserted before `paragraph`.

#### Layer 3 — AST (`Document.scala`)

New enum:
```scala
enum AdmonitionKind derives CanEqual:
  case Note, Tip, Important, Caution, Warning
```

New block:
```scala
case class Admonition(kind: AdmonitionKind, blocks: List[Block])(val span: Span)
    extends Block derives CanEqual
```

For paragraph form, `blocks` contains a single `Paragraph`. This unified structure keeps consumers simple — they never need to distinguish "is this an admonition paragraph or block?"; they just traverse `blocks`.

#### Layer 4 — Lowering (`CstLowering.scala`)

`lowerBlock` gains:
```scala
case CstAdmonitionParagraph(kind, content) =>
  val k = parseAdmonitionKind(kind)  // "NOTE" -> AdmonitionKind.Note, etc.
  Some(Admonition(k, List(Paragraph(lowerInlines(content))(block.span)))(block.span))
```

The delimited block path (`CstDelimitedBlock(Example, ...)`) is unchanged — it still lowers to `ast.Example`.

#### Layer 5 — ASG Bridge (`AstToAsg.scala`)

New match arm in `convertBlock`:
```scala
case ast.Admonition(kind, blocks) =>
  asg.Admonition(
    form = "paragraph",
    delimiter = "",
    variant = kind.toString.toLowerCase,
    blocks = Chunk.from(blocks.flatMap(convertBlockOpt)),
    location = inclusiveLocation(block.span)
  )
```

Existing `ast.Example` masquerade handling is untouched.

#### Layer 6 — AST Renderer (`AsciiDocRenderer.scala`)

New case:
```scala
case Admonition(kind, blocks) =>
  val label = kind.toString.toUpperCase
  blocks match
    case List(Paragraph(content)) =>
      sb.append(label).append(": ").append(renderInlines(content)).append('\n')
    case _ =>
      // Rare: multi-block admonition from paragraph form — render as NOTE-prefixed paragraph
      sb.append(label).append(": ").append(renderBlock(blocks.head)).append('\n')
```

#### Layer 7 — AST Visitor (`AstVisitor.scala`)

Add `visitAdmonition(node: Admonition): A = visitBlock(node)` with dispatch in the traversal method.

### Data flow

```
"NOTE: Watch out."
  → CstAdmonitionParagraph("NOTE", [CstText("Watch out.")])
  → ast.Admonition(AdmonitionKind.Note, [Paragraph([Text("Watch out.")])])
  → asg.Admonition(form="paragraph", delimiter="", variant="note", blocks=[Paragraph(...)])
```

```
"[NOTE]\n====\nWatch out.\n===="
  → CstDelimitedBlock(Example, attrs=[NOTE], ...)      (unchanged)
  → ast.Example(delim, [Paragraph(...)], attrs=[NOTE]) (unchanged)
  → asg.Admonition(form="delimited", delimiter="====", variant="note", ...)
```

---

## Files Changed

| File | Change |
|------|--------|
| `ascribe/src/.../lexer/AsciiDocLexer.scala` | Exclude `{`/`}` from content chars |
| `ascribe/src/.../cst/CstNodes.scala` | Add `CstAttributeRef`, `CstAdmonitionParagraph`; extend `CstAttributeEntry` |
| `ascribe/src/.../parser/InlineParser.scala` | Add `attrRefInline` parser |
| `ascribe/src/.../parser/BlockParser.scala` | Add `admonitionParagraphBlock`; extend `attributeEntryBlock` for `:!name:` |
| `ascribe/src/.../cst/CstLowering.scala` | Attribute map build + resolution; admonition paragraph lowering |
| `ascribe/src/.../cst/CstRenderer.scala` | Handle `CstAttributeRef`, `CstAdmonitionParagraph`, `CstAttributeEntry(unset=true)` |
| `ascribe/src/.../cst/CstVisitor.scala` | Dispatch for new CST nodes |
| `ascribe/src/.../ast/Document.scala` | Add `AdmonitionKind`, `Admonition` |
| `ascribe/src/.../ast/AstVisitor.scala` | Add `visitAdmonition` |
| `ascribe/bridge/.../AstToAsg.scala` | Convert `ast.Admonition` → `asg.Admonition` (paragraph form) |
| `ascribe/pipeline/.../AsciiDocRenderer.scala` | Render `ast.Admonition` |

### Test files (new + updated)

| File | Scope |
|------|-------|
| `ascribe/test/.../parser/InlineParserSpec.scala` | `{name}` parsing, unmatched `{`, nested markup inside ref name rejection |
| `ascribe/test/.../parser/BlockParserSpec.scala` | `NOTE: text`, `:!name:`, attribute entries |
| `ascribe/test/.../cst/CstLoweringSpec.scala` | Attr resolution, unset, built-ins, body entries, admonition paragraph |
| `ascribe/test/.../cst/CstRendererSpec.scala` | Round-trip for new CST nodes |
| `ascribe/test/.../cst/CstVisitorSpec.scala` | Traversal of new CST nodes |
| `ascribe/test/.../ast/AstVisitorSpec.scala` | Traversal of `Admonition` |
| `ascribe/bridge/test/.../AstToAsgSpec.scala` | `ast.Admonition` → `asg.Admonition` |
| `ascribe/pipeline/test/.../AsciiDocRendererSpec.scala` | Render and round-trip for admonitions |

---

## Error Handling

- Unresolved attribute ref `{name}` → passes through as literal `{name}` text (no error, standard AsciiDoc behaviour)
- Unknown admonition label (e.g. `DANGER:`) → not matched by parser, falls through to paragraph

---

## Testing Strategy

Unit tests cover each layer independently (parser → lowering → bridge → renderer). Integration tests via the existing `CstParserSpec` + `AscribeSpec` round-trip pattern. No TCK tests exist for these features yet; custom tests added inline following the existing `@specStatus` annotation pattern.
