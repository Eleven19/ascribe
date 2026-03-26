# Link and URL Inline Macros — Core Pass

**Date:** 2026-03-26
**Issue:** ascribe-4wq
**Status:** Approved

## Scope

Parse 4 link forms with plain link text (no attribute parsing in brackets).
Follow-up beads filed for full AsciiDoc spec conformance.

## Syntax Forms

| Form | Example | CST Node |
|------|---------|----------|
| Bare autolink | `https://example.com` | `CstAutolink` |
| URL macro | `https://example.com[Link text]` | `CstUrlMacro` |
| Link macro | `link:relative/path[text]` | `CstLinkMacro` |
| Mailto macro | `mailto:user@host[Email me]` | `CstMailtoMacro` |

Recognized URL schemes: `https://`, `http://`, `ftp://`, `irc://`.

## CST Nodes (CstNodes.scala)

Syntax-preserving nodes for round-tripping:

```scala
case class CstAutolink(target: String) extends CstInline
case class CstUrlMacro(target: String, text: List[CstInline]) extends CstInline
case class CstLinkMacro(target: String, text: List[CstInline]) extends CstInline
case class CstMailtoMacro(target: String, text: List[CstInline]) extends CstInline
```

## AST Nodes (Document.scala)

Semantic nodes — URL macro and link macro collapse into `Link`:

```scala
case class AutoLink(target: String) extends Inline
case class Link(target: String, text: List[Inline]) extends Inline
case class MailtoLink(target: String, text: List[Inline]) extends Inline
```

Empty `text` list means no display text was provided (e.g., `link:path[]`).
The renderer decides how to display empty-text links.

## Parser Rules (InlineParser.scala)

Priority ordering (check macros before bare URLs to avoid partial matches):

1. **`link:` macro** — `atomic("link:" ~> targetChars <~> bracketedContent)`
2. **`mailto:` macro** — `atomic("mailto:" ~> emailChars <~> bracketedContent)`
3. **URL macro** — `atomic(urlWithScheme <~> "[" ~> inlineContent <~ "]")` — scheme followed by `[`
4. **Bare autolink** — `atomic(urlWithScheme)` — scheme not followed by `[`

### Target character rules

- **Autolink targets**: consume until whitespace or end-of-line. Strip trailing punctuation (`.`, `,`, `;`, `:`).
- **Macro targets** (link:/mailto:/URL macro): consume until `[`.
- **Bracketed content**: parse inline elements between `[` and `]`.

## Lowering (CstLowering.scala)

```
CstAutolink(t)           → AutoLink(t)
CstUrlMacro(t, text)     → Link(t, lowerInlines(text))
CstLinkMacro(t, text)    → Link(t, lowerInlines(text))
CstMailtoMacro(t, text)  → MailtoLink(t, lowerInlines(text))
```

## ASG Bridge (AstToAsg.scala)

All three AST types map to `Ref(variant="link")`:

- `AutoLink(t)` → `Ref("link", t, [])`
- `Link(t, text)` → `Ref("link", t, convertInlines(text))`
- `MailtoLink(t, text)` → `Ref("link", "mailto:" + t, convertInlines(text))`

## CstRenderer (CstRenderer.scala)

Round-trip rendering reconstructs original syntax:

```
CstAutolink(t)           → t
CstUrlMacro(t, text)     → t + "[" + renderInlines(text) + "]"
CstLinkMacro(t, text)    → "link:" + t + "[" + renderInlines(text) + "]"
CstMailtoMacro(t, text)  → "mailto:" + t + "[" + renderInlines(text) + "]"
```

## Test DSL (dsl.scala)

```scala
def autoLink(target: String): AutoLink
def link(target: String, text: Inline*): Link
def mailtoLink(target: String, text: Inline*): MailtoLink
```

## Files Changed

| File | Change |
|------|--------|
| `CstNodes.scala` | Add 4 CST inline node types |
| `Document.scala` | Add 3 AST inline node types |
| `InlineParser.scala` | Add link/URL parsers with priority ordering |
| `CstLowering.scala` | Add 4 match cases |
| `CstRenderer.scala` | Add 4 render cases |
| `AstToAsg.scala` | Add 3 → `Ref` conversions |
| `dsl.scala` | Add 3 DSL constructors |
| `InlineParserSpec.scala` | Tests for all 4 forms + edge cases |
| `CstLoweringSpec.scala` | Lowering tests for link nodes |

## Deferred (follow-up beads)

- Link attributes (`window`, `role`, `opts`, `^` shorthand)
- Angle-bracketed URLs (`<https://...>`)
- Bare email autolinks (`user@example.com` without `mailto:`)
- Backslash escaping to suppress auto-linking
- Passthrough targets (`link:pass:[path with spaces][]`)
- `hide-uri-scheme` document attribute
- mailto subject/body positional parameters
- Trailing punctuation stripping edge cases
- URLs with special characters (underscores triggering inline formatting)
