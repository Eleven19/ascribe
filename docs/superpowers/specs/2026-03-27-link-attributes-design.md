# Link Macro Attribute Parsing

**Date:** 2026-03-27
**Issue:** ascribe-mla
**Status:** Approved

## Scope

Add generic inline macro attribute parsing to bracket content. Parse link text,
named attributes (`window`, `role`, `opts`, `id`, `title`), and `^` shorthand.
Reusable for future image/xref/footnote macros.

## Detection Rule

Bracket content is parsed as an attribute list when it contains a comma (`,`) or
equals sign (`=`) outside of quotes. Otherwise it's treated as plain link text
(first positional only). Quoted text (`"..."`) protects commas/equals from
triggering attribute mode.

## CST — Generic Macro Attribute List

New shared node for all inline macro bracket content:

```scala
case class CstMacroAttrList(
    text: List[CstInline],          // first positional, parsed as inline content
    positional: List[String],       // remaining positional attrs (raw strings)
    named: List[(String, String)],  // named attrs preserving source order
    hasCaretShorthand: Boolean      // ^ at end of text
)(val span: Span) extends CstNode derives CanEqual
```

CST link nodes change `text: List[CstInline]` to `attrList: CstMacroAttrList`:

```scala
case class CstUrlMacro(target: String, attrList: CstMacroAttrList)(val span: Span) extends CstLink
case class CstLinkMacro(target: String, attrList: CstMacroAttrList)(val span: Span) extends CstLink
case class CstMailtoMacro(target: String, attrList: CstMacroAttrList)(val span: Span) extends CstLink
```

## Parser — Two-Phase Bracket Parsing

The `bracketedInlineContent` parser is replaced with `macroAttrList`:

1. **Phase 1**: Consume raw content between `[` and `]` as a string
2. **Phase 2**: If the raw string contains `,` or `=` (outside quotes), parse as
   attribute list. Otherwise treat entire content as first positional (re-parse
   as inlines).

For attribute list mode:
- Split on commas (respecting quotes)
- First segment = first positional (display text), parsed as inlines
- Remaining segments: if contains `=` → named attribute; else → positional
- Detect trailing `^` on first positional and set `hasCaretShorthand`
- Quoted values strip surrounding `"`; backslash-escaped `\"` supported

## AST — Domain-Typed Link Attributes

### Opaque types with extractors

```scala
opaque type ElementId <: String = String
object ElementId:
    def apply(value: String): ElementId = value
    def unapply(id: ElementId): Some[String] = Some(id)

opaque type WindowTarget <: String = String
object WindowTarget:
    def apply(value: String): WindowTarget = value
    def unapply(target: WindowTarget): Some[String] = Some(target)
    val Blank: WindowTarget = "_blank"

opaque type CssRole <: String = String
object CssRole:
    def apply(value: String): CssRole = value
    def unapply(role: CssRole): Some[String] = Some(role)

enum LinkOption:
    case NoFollow, NoOpener
```

### LinkAttributes case class

```scala
case class LinkAttributes(
    id: Option[ElementId] = None,
    title: Option[String] = None,
    window: Option[WindowTarget] = None,
    roles: List[CssRole] = Nil,
    options: Set[LinkOption] = Set.empty
)

object LinkAttributes:
    val empty: LinkAttributes = LinkAttributes()

    /** Matches links that open in a new window/tab. */
    object OpensInNewWindow:
        def unapply(attrs: LinkAttributes): Option[WindowTarget] =
            attrs.window.filter(_ == WindowTarget.Blank)
```

### Updated Link AST node

```scala
case class Link(
    variant: LinkVariant,
    target: String,
    text: List[Inline],
    attributes: LinkAttributes
)(val span: Span) extends Inline
```

## Lowering — Interpretation

`CstMacroAttrList` → `(List[Inline], LinkAttributes)`:

- `^` shorthand → `window = Some(WindowTarget.Blank)`, implicitly add `NoOpener`
- `window=_blank` → `window = Some(WindowTarget.Blank)`, implicitly add `NoOpener`
- `window=X` (other) → `window = Some(WindowTarget("X"))`
- `role=X` → `roles = List(CssRole("X"))`
- `opts=nofollow` → `options += NoFollow`
- `opts="noopener,nofollow"` → split on comma, map each
- `id=X` → `id = Some(ElementId("X"))`
- `title=X` → `title = Some("X")`
- Unknown named attributes → ignored for now

## CstRenderer — Round-Trip

Reconstructs original bracket syntax from `CstMacroAttrList`:

- Render first positional text as inlines
- Append `^` if `hasCaretShorthand`
- Append remaining positional attributes separated by commas
- Append named attributes as `key=value` separated by commas
- Wrap in `[` and `]`

## ASG Bridge

`LinkAttributes` maps to ASG `Ref` attributes. The ASG `Ref` type has `variant`
and `target` fields. Link attributes that affect the output (like `role`, `window`,
`id`) should be represented if the ASG schema supports them. If not, they are
preserved through the AST layer for rendering but dropped at the ASG level.

## Files Changed

| File | Change |
|------|--------|
| `CstNodes.scala` | Add `CstMacroAttrList`; change link CST nodes to use it |
| `Document.scala` | Add opaque types, `LinkOption`, `LinkAttributes`; update `Link` |
| `InlineParser.scala` | Replace `bracketedInlineContent` with `macroAttrList` parser |
| `CstLowering.scala` | Interpret `CstMacroAttrList` → `LinkAttributes` |
| `CstRenderer.scala` | Render `CstMacroAttrList` round-trip |
| `AstToAsg.scala` | Map `LinkAttributes` to ASG |
| `dsl.scala` | Update link DSL constructors for `LinkAttributes` |
| `AstVisitor.scala` | No structural change (Link node type unchanged) |
| `CstVisitor.scala` | Update children for new link CST structure |
| Pipeline files | Update `flattenInlines`, renderers for new `Link` signature |
| Test files | Parser, lowering, bridge, renderer tests |

## Test Cases

### Parser tests

- `link:path[text]` → `CstMacroAttrList(text=[CstText("text")], positional=[], named=[], caret=false)`
- `link:path[text,window=_blank]` → text + `named=[("window","_blank")]`
- `link:path[text^]` → text="text", `caret=true`
- `link:path["text, with comma",role=btn]` → quoted text preserved, role parsed
- `link:path[role=btn]` → empty text, `named=[("role","btn")]`
- `https://example.com[]` → empty text, no attributes
- `link:path[text,window=_blank,opts=nofollow]` → text + two named attrs
- `link:path["text with \"quotes\""]` → escaped quotes in text
- `mailto:user@host[text,subject,body]` → text + two remaining positionals

### Lowering tests

- `caret=true` → `LinkAttributes(window=Some(Blank), options=Set(NoOpener))`
- `window=_blank` → same
- `role=btn` → `roles=List(CssRole("btn"))`
- `opts=nofollow` → `options=Set(NoFollow)`
- `opts="noopener,nofollow"` → `options=Set(NoOpener, NoFollow)`
- empty attrs → `LinkAttributes.empty`

### Round-trip renderer tests

- Parse → render → should reproduce original bracket content
