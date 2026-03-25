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

1. `{attribute-name}` inline references in all inline contexts (paragraphs, headings, list items, block titles, table cells — everywhere `lowerInlines` is called)
2. Attribute entry unset form: `:!name:` (removes the attribute from scope)
3. Body-level attribute entries that affect subsequent content (not just header)
4. Built-in special attributes: `{empty}`, `{nbsp}`, `{zwsp}`, `{sp}`

Out of scope for this issue: soft-set form `:name!:` (no CLI yet), attribute substitution inside attribute values (recursive substitution).

### Architecture

Four-layer change across: lexer → CST → inline/block parsers → lowering.

#### Layer 1 — Lexer (`AsciiDocLexer.scala`)

Add `{` and `}` to the set of characters excluded from `isContentChar`. This prevents `{` from being greedily consumed as plain text before the inline parser has a chance to recognize it as an attribute reference opener.

Add both `{` and `}` to `unpairedMarkupChar` so unmatched delimiters fall back gracefully to `CstText("{")` / `CstText("}")`. This covers content like `{unknown}` (unresolved ref) as well as lone `{` or `}` characters.

#### Layer 2 — CST Nodes (`CstNodes.scala`)

New inline node:
```scala
case class CstAttributeRef(name: String)(val span: Span) extends CstInline derives CanEqual
```

**Breaking change to `CstAttributeEntry`:** The existing node currently has no `unset` field (the comment in `CstNodes.scala` explicitly calls this out of scope for the prior iteration). Adding `unset` is a breaking change to a case class. Every pattern match on `CstAttributeEntry` in the codebase must be updated:

- `CstLowering.lowerBlock` — `case _: CstAttributeEntry => None` still works (wildcard, no update needed)
- `CstRenderer` — must emit `:!name:` when `unset = true` instead of `:name: value`
- `CstVisitor.children` — `case _: CstAttributeEntry => Nil` still works (wildcard, no update needed)
- `lowerHeader` — `h.attributes.map(e => (e.name, e.value))`: this still compiles. **Known limitation:** `DocumentHeader.attributes` is `List[(String, String)]` and has no unset semantics; an unset entry in the header is stored as `("name", "")` and the unset intent is lost at the AST level. Header-level unsets are rare in practice (they apply to built-in attributes). The attribute map built in `toAst` is seeded from the header entries via `cst.header.toList.flatMap(_.attributes)` — since `CstAttributeEntry(name, "", unset=true)` entries are in this list, the lowering map logic (which processes unsets correctly) takes precedence. The AST `DocumentHeader.attributes` field preserving `("name","")` is a minor inaccuracy but does not affect resolution. This is tracked as a known limitation.

Updated definition:
```scala
case class CstAttributeEntry(name: String, value: String, unset: Boolean)(val span: Span)
    extends CstBlock derives CanEqual
```
`unset = true` when the form `:!name:` is used (value is always `""` in this case).

#### Layer 3 — Inline Parser (`InlineParser.scala`)

New parser `attrRefInline`:
- Matches `{` followed by one or more `[a-zA-Z][a-zA-Z0-9_-]*` chars followed by `}` (name must start with a letter, per AsciiDoc spec)
- Produces `CstAttributeRef(name)`
- Unmatched `{` or `}` → falls back to `unpairedMarkupInline` → `CstText("{")` / `CstText("}")`

Exact resulting order in `inlineElement`:
```scala
val inlineElement: Parsley[CstInline] =
    boldSpan | constrainedBoldSpan | italicSpan | monoSpan |
        attrRefInline | plainTextInline | unpairedMarkupInline
```
`attrRefInline` must come before `plainTextInline` (since `{` is no longer a content char) and after `monoSpan`.

#### Layer 4 — Block Parser (`BlockParser.scala`)

`attributeEntryBlock` updated to recognise `:!name:` form:
- After matching the opening `:`, if the next char is `!`, consume it, strip the `!` prefix from the name, and set `unset = true`
- Otherwise `unset = false` as before

#### Layer 5 — Lowering (`CstLowering.scala`)

**Attribute map threading mechanism:** Rather than adding a parameter to `lowerBlock` (which would require updating all recursive internal calls for nested delimited blocks), `toAst` is refactored so that `lowerBlock`, `lowerInlines`, and `lowerInline` become **local `def`s inside `toAst`** that close over a `var attributeMap`. This means all recursive calls — including nested delimited block lowering — automatically see the same map without any signature changes:

```scala
def toAst(cst: CstDocument): Document =
    val header = cst.header.map(lowerHeader)
    // Build initial attribute map from built-ins + header attributes
    var attributeMap: Map[String, String] =
        Map("empty" -> "", "sp" -> " ", "nbsp" -> "\u00A0", "zwsp" -> "\u200B") ++
            cst.header.toList.flatMap(_.attributes).map(e => e.name -> e.value)

    def lowerInline(inline: CstInline): Inline = inline match
        // ... existing cases ...
        case CstAttributeRef(name) =>
            Text(attributeMap.getOrElse(name, s"{$name}"))(inline.span)

    def lowerInlines(inlines: List[CstInline]): List[Inline] = inlines.map(lowerInline)

    def lowerBlock(block: CstBlock): Option[Block] = block match
        // ... existing cases, unchanged signatures, all close over attributeMap via lowerInlines ...

    // Process body blocks sequentially, updating attributeMap as entries are encountered
    val blocks = cst.content
        .collect { case b: CstBlock => b }
        .flatMap {
            case CstAttributeEntry(name, value, false) => attributeMap = attributeMap + (name -> value); None
            case CstAttributeEntry(name, _, true)      => attributeMap = attributeMap - name; None
            case other                                 => lowerBlock(other)
        }
    val restructured = restructure(blocks)
    Document(header, restructured)(cst.span)
```

The existing private `lowerBlock`, `lowerInlines`, and `lowerInline` methods on `CstLowering` become private helpers called by the local defs, or are inlined. All recursive calls within delimited block lowering continue to work without change because they call the local `lowerBlock` def which closes over the same `attributeMap`.

Unresolved references pass through as literal `{name}` text — standard AsciiDoc behaviour.

**Scope note:** Because `lowerInline` is called transitively from heading titles, list items, block titles, table cells, and all other inline contexts, attribute references are automatically resolved everywhere — consistent with the AsciiDoc spec which applies attribute substitution to all inline contexts except verbatim/pass content.

Built-in initial values:
| Reference | Value |
|-----------|-------|
| `{empty}` | `""` |
| `{sp}`    | `" "` |
| `{nbsp}`  | `"\u00A0"` |
| `{zwsp}`  | `"\u200B"` |

### Data flow

```
":version: 1.0"  →  CstAttributeEntry("version","1.0",false)  →  map["version"]="1.0"
"{version}"      →  CstAttributeRef("version")                →  Text("1.0")
":!version:"     →  CstAttributeEntry("version","",true)      →  map.remove("version")
"{version}"      →  CstAttributeRef("version")                →  Text("{version}")
```

#### Layer 6 — CST Renderer (`CstRenderer.scala`)

Three new cases for CST round-trip fidelity:
- `CstAttributeRef(name)` → renders as `{name}`
- `CstAttributeEntry(name, _, true)` → renders as `:!name:`
- (Existing `CstAttributeEntry` rendering continues for `unset=false`)

### No AST/ASG changes

The AST gains no new inline node. The ASG layer is untouched. No AST renderer changes needed for Feature 1.

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

**ASG schema note:** The `asg.Admonition` Scala class accepts any `String` for `form` and `delimiter`. The current ASG JSON schema specifies `form: { const: "delimited" }` for `parentBlock` types, covering only the delimited admonition form. Using `form = "paragraph"` for paragraph-form admonitions is an extension of the schema not yet validated by the JSON schema. This is intentional and consistent with the AsciiDoc spec (paragraph admonitions are a first-class form); it should be tracked as a schema-extension item for future alignment with the AsciiDoc Language Spec schema.

Existing `ast.Example` masquerade handling is untouched.

#### Layer 6 — AST Renderer (`AsciiDocRenderer.scala`)

New case in `renderBlockTo`:
```scala
case Admonition(kind, blocks) =>
  val label = kind.toString.toUpperCase
  blocks match
    case List(Paragraph(content)) =>
      sb.append(label).append(": ").append(renderInlines(content)).append('\n')
    case _ =>
      // Multi-block admonition: render all blocks (renderBlocks handles separators)
      renderBlocks(blocks, sb)
```

Note: the multi-block case uses `renderBlocks` (the existing list-rendering helper), not `renderBlock(blocks.head)`, to avoid infinite recursion if any nested block is itself an `Admonition`.

#### Layer 7 — AST Visitor (`AstVisitor.scala`)

Three additions required:

1. New method in the `AstVisitor` trait:
   ```scala
   def visitAdmonition(node: Admonition): A = visitBlock(node)
   ```
2. Dispatch arm in `AstVisitor.visit`:
   ```scala
   case n: Admonition => visitor.visitAdmonition(n)
   ```
3. Children case in `AstVisitor.children`:
   ```scala
   case a: Admonition => a.blocks
   ```
   Without this, traversal-based tools (`collect`, `foldLeft`) would silently miss all content nested inside an `Admonition`.

#### Layer 8 — CST Visitor (`CstVisitor.scala`)

Two new `children` cases required:
```scala
case ap: CstAdmonitionParagraph => ap.content  // inline children are traversable
case _: CstAttributeRef         => Nil          // leaf inline, no children
```

Plus dispatch arms in `CstVisitor.visit` for the new node types.

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
| `ascribe/src/.../lexer/AsciiDocLexer.scala` | Exclude `{`/`}` from content chars; add both to unpaired markup fallback |
| `ascribe/src/.../cst/CstNodes.scala` | Add `CstAttributeRef`; add `CstAdmonitionParagraph`; add `unset: Boolean` to `CstAttributeEntry` (breaking change) |
| `ascribe/src/.../parser/InlineParser.scala` | Add `attrRefInline`; update `inlineElement` order |
| `ascribe/src/.../parser/BlockParser.scala` | Add `admonitionParagraphBlock`; extend `attributeEntryBlock` for `:!name:` |
| `ascribe/src/.../cst/CstLowering.scala` | Refactored: `lowerBlock`/`lowerInlines`/`lowerInline` become local defs closing over `var attributeMap`; sequential fold for body blocks; admonition paragraph lowering |
| `ascribe/src/.../cst/CstRenderer.scala` | Handle `CstAttributeRef` (render as `{name}`); `CstAdmonitionParagraph` (render as `KIND: text`); `CstAttributeEntry(unset=true)` (render as `:!name:`) |
| `ascribe/src/.../cst/CstVisitor.scala` | Dispatch + children for `CstAttributeRef` and `CstAdmonitionParagraph` |
| `ascribe/src/.../ast/Document.scala` | Add `AdmonitionKind` enum; add `Admonition` block |
| `ascribe/src/.../ast/AstVisitor.scala` | Add `visitAdmonition` method, dispatch arm, and children case |
| `ascribe/bridge/.../AstToAsg.scala` | Convert `ast.Admonition` → `asg.Admonition` (paragraph form) |
| `ascribe/pipeline/.../AsciiDocRenderer.scala` | Render `ast.Admonition` |

### Test files (new + updated)

| File | Scope |
|------|-------|
| `ascribe/test/.../parser/InlineParserSpec.scala` | `{name}` parsing, unmatched `{`/`}`, valid attr ref name chars |
| `ascribe/test/.../parser/BlockParserSpec.scala` | `NOTE: text` all five types, `:!name:`, attribute entries with unset |
| `ascribe/test/.../cst/CstLoweringSpec.scala` | Attr resolution, unset, built-ins, body entries, admonition paragraph, attr refs in headings/list items |
| `ascribe/test/.../cst/CstRendererSpec.scala` | Round-trip for `CstAttributeRef`, `CstAdmonitionParagraph`, unset entry |
| `ascribe/test/.../cst/CstVisitorSpec.scala` | Traversal of `CstAttributeRef` and `CstAdmonitionParagraph` |
| `ascribe/test/.../ast/AstVisitorSpec.scala` | Traversal of `ast.Admonition`; children collected correctly |
| `ascribe/bridge/test/.../AstToAsgSpec.scala` | `ast.Admonition` → `asg.Admonition` (paragraph form) |
| `ascribe/pipeline/test/.../AsciiDocRendererSpec.scala` | Render and round-trip for admonitions |

---

## Error Handling

- Unresolved attribute ref `{name}` → passes through as literal `{name}` text (no error, standard AsciiDoc behaviour)
- Unknown admonition label (e.g. `DANGER:`) → not matched by parser, falls through to paragraph

---

## Testing Strategy

Unit tests cover each layer independently (parser → lowering → bridge → renderer). Integration tests via the existing `CstParserSpec` + `AscribeSpec` round-trip pattern. No TCK tests exist for these features yet; custom tests added inline following the existing `@specStatus` annotation pattern.
