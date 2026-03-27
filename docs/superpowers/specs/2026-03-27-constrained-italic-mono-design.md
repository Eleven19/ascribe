# Constrained Italic and Monospace Inline Formatting

**Date:** 2026-03-27
**Issue:** ascribe-eib
**Status:** Approved

## Scope

Add constrained italic (`_text_`) and monospace (`` `text` ``) parsers with spec-compliant
word-boundary enforcement. Retrofit constrained bold (`*text*`) to also enforce boundaries.

## AsciiDoc Spec Rules

Constrained formatting (single delimiter pair) requires:

- **Before opening mark**: space-like character or start of line (NOT a word character)
- **After closing mark**: space-like character, punctuation (`,`, `;`, `"`, `.`, `?`, `!`), or end of line
- **Content**: cannot start or end with space-like characters

Word characters: letters, digits, underscore (`_`).

Unconstrained formatting (double delimiter pair) has no boundary requirements.

## CST Changes (CstNodes.scala)

Add `constrained: Boolean` to `CstItalic` and `CstMono`, matching the `CstBold` pattern.
Remove the existing TODO comments.

```scala
case class CstItalic(content: List[CstInline], constrained: Boolean)(val span: Span)
    extends CstInline derives CanEqual

case class CstMono(content: List[CstInline], constrained: Boolean)(val span: Span)
    extends CstInline derives CanEqual
```

Breaking change — all existing `CstItalic(content)` and `CstMono(content)` call sites
need the boolean added (with `constrained = false` for existing unconstrained uses).

## AST Changes (Document.scala)

Add `ConstrainedItalic` and `ConstrainedMono` case classes, following the `Bold`/`ConstrainedBold` pattern:

```scala
case class ConstrainedItalic(content: List[Inline])(val span: Span) extends Inline derives CanEqual
case class ConstrainedMono(content: List[Inline])(val span: Span) extends Inline derives CanEqual
```

## Parser Changes (InlineParser.scala)

### Word-boundary state tracking

Use Parsley `Ref[Option[Char]]` to track the last consumed character:

```scala
import parsley.state.Ref

private val lastChar: Ref[Option[Char]] = Ref.make[Option[Char]]
```

### Boundary predicates

```scala
private def isWordChar(c: Char): Boolean = c.isLetterOrDigit || c == '_'

private def isConstrainedClose(c: Char): Boolean =
    c == ' ' || c == '\t' || c == '\n' || c == '\r' ||
    c == ',' || c == ';' || c == '"' || c == '.' || c == '?' || c == '!'
```

### Constrained opening check

Last char must be `None` (SOL) or not a word char:

```scala
private val atConstrainedOpen: Parsley[Unit] =
    lastChar.get.flatMap {
        case None    => unit
        case Some(c) => if !isWordChar(c) then unit else empty
    }
```

### Constrained closing check

Next char must be space/punct/EOL:

```scala
private val atConstrainedClose: Parsley[Unit] =
    lookAhead(satisfy(isConstrainedClose)).void | eof
```

### New constrained parsers

```scala
val constrainedItalicSpan: Parsley[CstInline] =
    atomic(atConstrainedOpen *> (pos <~> delimitedContent("_", "_") <~> pos) <* atConstrainedClose)
        .map { case ((s, content), e) =>
            val span = mkSpan(s, e)
            CstItalic(List(CstText(content)(span)), constrained = true)(span)
        }
        .label("constrained italic span")

val constrainedMonoSpan: Parsley[CstInline] =
    atomic(atConstrainedOpen *> (pos <~> delimitedContent("`", "`") <~> pos) <* atConstrainedClose)
        .map { case ((s, content), e) =>
            val span = mkSpan(s, e)
            CstMono(List(CstText(content)(span)), constrained = true)(span)
        }
        .label("constrained monospace span")
```

### Retrofit constrained bold

Update existing `constrainedBoldSpan` to also use `atConstrainedOpen` and `atConstrainedClose`.

### Update inlineElement and lineContent

Priority order: unconstrained (double delimiters) before constrained (single delimiters).

```scala
val inlineElement: Parsley[CstInline] =
    boldSpan | italicSpan | monoSpan |
        constrainedBoldSpan | constrainedItalicSpan | constrainedMonoSpan |
        linkMacro | mailtoMacro | urlMacro | autolink |
        attrRefInline | plainTextInline | unpairedMarkupInline
```

Initialize and update the `lastChar` ref:

```scala
val lineContent: Parsley[List[CstInline]] =
    lastChar.set(None) *> many(inlineElement)
```

Each inline element that consumes characters updates the ref. The key update points:
- `plainTextInline`: set ref to last char of consumed text
- `unpairedMarkupInline`: set ref to the consumed char
- Delimited spans (bold, italic, mono, link macros): set ref to the closing delimiter char

## Lowering (CstLowering.scala)

```
CstItalic(content, false) → Italic(lowerInlines(content))
CstItalic(content, true)  → ConstrainedItalic(lowerInlines(content))
CstMono(content, false)   → Mono(lowerInlines(content))
CstMono(content, true)    → ConstrainedMono(lowerInlines(content))
```

## CstRenderer (CstRenderer.scala)

```
CstItalic(content, false) → "__" + renderInlines(content) + "__"
CstItalic(content, true)  → "_" + renderInlines(content) + "_"
CstMono(content, false)   → "``" + renderInlines(content) + "``"
CstMono(content, true)    → "`" + renderInlines(content) + "`"
```

## ASG Bridge (AstToAsg.scala)

```
ConstrainedItalic(content) → Span("emphasis", "constrained", convertInlines(content), loc)
ConstrainedMono(content)   → Span("code", "constrained", convertInlines(content), loc)
```

## AstVisitor

Add methods following the existing Bold/ConstrainedBold pattern:

```scala
def visitConstrainedItalic(node: ConstrainedItalic): A = visitInline(node)
def visitConstrainedMono(node: ConstrainedMono): A     = visitInline(node)
```

Plus dispatch and children cases.

## Test DSL (dsl.scala)

```scala
def constrainedItalic(inlines: Inline*): ConstrainedItalic = ConstrainedItalic(inlines.toList)(u)
def constrainedMono(inlines: Inline*): ConstrainedMono     = ConstrainedMono(inlines.toList)(u)
```

## Pipeline Updates

Add cases to `flattenInlines`, `AsciiDocRenderer.renderInline`, and `stripFormatting`
for `ConstrainedItalic` and `ConstrainedMono`.

## Files Changed

| File | Change |
|------|--------|
| `CstNodes.scala` | Add `constrained: Boolean` to CstItalic, CstMono; remove TODOs |
| `Document.scala` | Add ConstrainedItalic, ConstrainedMono case classes + companions |
| `InlineParser.scala` | Add Ref-based boundary checking, constrained parsers, retrofit bold |
| `AsciiDocLexer.scala` | Possibly add `` ` `` to unpairedMarkupChar if not already present |
| `CstLowering.scala` | Update match cases for constrained boolean |
| `CstRenderer.scala` | Add constrained render cases |
| `AstToAsg.scala` | Add ConstrainedItalic/Mono → Span conversions |
| `AstVisitor.scala` | Add visit methods, dispatch cases, children cases |
| `dsl.scala` | Add constrainedItalic, constrainedMono constructors |
| `pipeline/dsl.scala` | Add cases to flattenInlines, stripFormatting |
| `pipeline/AsciiDocRenderer.scala` | Add render cases |
| `itest/AsciiDocParserSteps.scala` | Add case to inlinesToText |
| `InlineParserSpec.scala` | Constrained italic/mono parser tests + boundary tests |
| `CstLoweringSpec.scala` | Lowering tests for constrained variants |
| `AstVisitorSpec.scala` | Visitor dispatch tests |
| `CstVisitorSpec.scala` | CST visitor tests |
| `CstRendererSpec.scala` | Render round-trip tests |
| `AsciiDocRendererSpec.scala` | AST render tests |

## Test Cases

### Parser tests — constrained italic
- `_italic_` → CstItalic(constrained=true)
- `hello _world_ end` → text, constrained italic, text
- `foo_bar_baz` → text (NOT italic — word chars on both sides)
- `(_italic_)` → text, constrained italic, text (punctuation boundary)

### Parser tests — constrained monospace
- `` `code` `` → CstMono(constrained=true)
- `` hello `code` end `` → text, constrained mono, text
- `` foo`bar`baz `` → text (NOT mono — word chars on both sides)

### Parser tests — constrained bold (retrofitted)
- `*bold*` → CstBold(constrained=true) (still works at SOL)
- `foo*bar*baz` → text (NOT bold — word chars on both sides)

### Parser tests — unconstrained still work
- `__italic__` → CstItalic(constrained=false) (no boundary check)
- ` ``mono`` ` → CstMono(constrained=false) (no boundary check)
- `**bold**` → CstBold(constrained=false) (no boundary check)
