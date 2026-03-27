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

Single `Link` node with a two-level variant hierarchy:

```scala
enum MacroKind:
  case MailTo
  case Link
  case Url(scheme: String)  // "https", "http", "ftp", "irc"

enum LinkVariant:
  case Auto                   // bare URL — text is implicitly the target
  case Macro(kind: MacroKind) // any macro form with target[text] syntax

case class Link(variant: LinkVariant, target: String, text: List[Inline]) extends Inline:
  /** Extracts the URL scheme from the target, if present. */
  lazy val scheme: Option[String] =
    target.indexOf("://") match
      case -1  => None
      case idx => Some(target.substring(0, idx))

object Link:
  /** Extractor for pattern-matching on the scheme: `case Link.Scheme(s) => ...` */
  object Scheme:
    def unapply(link: Link): Option[String] = link.scheme
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
CstAutolink(t)           → Link(Auto, t, Nil)
CstUrlMacro(t, text)     → Link(Macro(Url(scheme)), t, lowerInlines(text))
CstLinkMacro(t, text)    → Link(Macro(Link), t, lowerInlines(text))
CstMailtoMacro(t, text)  → Link(Macro(MailTo), t, lowerInlines(text))
```

## ASG Bridge (AstToAsg.scala)

All variants map to `Ref(variant="link")`:

- `Link(Auto, t, _)` → `Ref("link", t, [])`
- `Link(Macro(Url(_)), t, text)` → `Ref("link", t, convertInlines(text))`
- `Link(Macro(Link), t, text)` → `Ref("link", t, convertInlines(text))`
- `Link(Macro(MailTo), t, text)` → `Ref("link", "mailto:" + t, convertInlines(text))`

## CstRenderer (CstRenderer.scala)

Round-trip rendering reconstructs original syntax:

```
CstAutolink(t)           → t
CstUrlMacro(t, text)     → t + "[" + renderInlines(text) + "]"
CstLinkMacro(t, text)    → "link:" + t + "[" + renderInlines(text) + "]"
CstMailtoMacro(t, text)  → "mailto:" + t + "[" + renderInlines(text) + "]"
```

## Test DSL (dsl.scala)

Dedicated constructors per link kind, each returning `Link` with the appropriate variant:

```scala
def autoLink(target: String): Link =
  Link(LinkVariant.Auto, target, Nil)

def urlLink(scheme: String, target: String, text: Inline*): Link =
  Link(LinkVariant.Macro(MacroKind.Url(scheme)), target, text.toList)

def link(target: String, text: Inline*): Link =
  Link(LinkVariant.Macro(MacroKind.Link), target, text.toList)

def mailtoLink(target: String, text: Inline*): Link =
  Link(LinkVariant.Macro(MacroKind.MailTo), target, text.toList)
```

## Files Changed

| File | Change |
|------|--------|
| `CstNodes.scala` | Add 4 CST inline node types |
| `Document.scala` | Add `LinkVariant` enum and `Link` case class |
| `InlineParser.scala` | Add link/URL parsers with priority ordering |
| `CstLowering.scala` | Add 4 match cases |
| `CstRenderer.scala` | Add 4 render cases |
| `AstToAsg.scala` | Add variant-based `Ref` conversion |
| `dsl.scala` | Add `autoLink`, `urlLink`, `link`, `mailtoLink` constructors |
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
