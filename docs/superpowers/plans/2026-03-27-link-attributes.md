# Link Macro Attribute Parsing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parse link macro bracket content as structured attributes (text, named attrs, `^` shorthand) with domain-typed AST representation.

**Architecture:** Add `CstMacroAttrList` CST node for generic inline macro bracket content. Add `LinkAttributes` with opaque types (`ElementId`, `WindowTarget`, `CssRole`) and `LinkOption` enum to the AST `Link` node. Two-phase bracket parser: raw content capture then attribute detection via comma/equals outside quotes. Lowering interprets raw CST attrs into domain-typed `LinkAttributes`.

**Tech Stack:** Scala 3, Parsley parser combinators, ZIO Test, Mill build

**Spec:** `docs/superpowers/specs/2026-03-27-link-attributes-design.md`

**Test command:** `./mill ascribe.test.testLocal`

**Bridge test command:** `./mill ascribe.bridge.test.testLocal`

**All modules:** `./mill __.testLocal`

**Working directory:** `/home/damian/code/repos/github/Eleven19/ascribe/.worktrees/feat-link-attributes`

---

### Task 1: Add CstMacroAttrList and update CST link nodes

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstNodes.scala:175-185`

- [ ] **Step 1: Add CstMacroAttrList**

In `ascribe/src/io/eleven19/ascribe/cst/CstNodes.scala`, before the `CstLink` sealed trait (line 176), add:

```scala
// ── Inline macro attribute list ──────────────────────────────────────────────

case class CstMacroAttrList(
    text: List[CstInline],
    positional: List[String],
    named: List[(String, String)],
    hasCaretShorthand: Boolean
)(val span: Span)
    extends CstNode derives CanEqual

object CstMacroAttrList:
    def textOnly(text: List[CstInline])(span: Span): CstMacroAttrList =
        CstMacroAttrList(text, Nil, Nil, false)(span)

    val empty: Span => CstMacroAttrList = span =>
        CstMacroAttrList(Nil, Nil, Nil, false)(span)
```

- [ ] **Step 2: Update CST link nodes to use CstMacroAttrList**

Replace the three link macro case classes (lines 181-185):

```scala
case class CstUrlMacro(target: String, attrList: CstMacroAttrList)(val span: Span) extends CstLink derives CanEqual

case class CstLinkMacro(target: String, attrList: CstMacroAttrList)(val span: Span) extends CstLink derives CanEqual

case class CstMailtoMacro(target: String, attrList: CstMacroAttrList)(val span: Span) extends CstLink derives CanEqual
```

- [ ] **Step 3: Fix all compilation errors from the signature change**

The compiler will report every site that constructs or pattern-matches on `CstUrlMacro`, `CstLinkMacro`, `CstMailtoMacro` with the old `text: List[CstInline]` signature. Key files to update:

**InlineParser.scala** — each link parser constructs these nodes. Wrap `text` in `CstMacroAttrList.textOnly(text)(mkSpan(s, e))`:

For `linkMacro` (line 197):
```scala
CstLinkMacro(target, CstMacroAttrList.textOnly(text)(mkSpan(s, e)))(mkSpan(s, e))
```

For `mailtoMacro` (line 205):
```scala
CstMailtoMacro(target, CstMacroAttrList.textOnly(text)(mkSpan(s, e)))(mkSpan(s, e))
```

For `urlMacro` (line 213):
```scala
CstUrlMacro(target, CstMacroAttrList.textOnly(text)(mkSpan(s, e)))(mkSpan(s, e))
```

**CstLowering.scala** (lines 57-65) — update match patterns. Change `text` to `attrList` and access `attrList.text`:

```scala
case CstUrlMacro(target, attrList) =>
    val scheme = target.indexOf("://") match
        case -1  => ""
        case idx => target.substring(0, idx)
    Link(LinkVariant.Macro(MacroKind.Url(scheme)), target, lowerInlines(attrList.text))(inline.span)
case CstLinkMacro(target, attrList) =>
    Link(LinkVariant.Macro(MacroKind.Link), target, lowerInlines(attrList.text))(inline.span)
case CstMailtoMacro(target, attrList) =>
    Link(LinkVariant.Macro(MacroKind.MailTo), target, lowerInlines(attrList.text))(inline.span)
```

**CstRenderer.scala** (lines 187-201) — update match patterns and render the full attr list:

```scala
case CstUrlMacro(target, attrList) =>
    sb.append(target)
    sb.append('[')
    renderMacroAttrList(attrList, sb)
    sb.append(']')
case CstLinkMacro(target, attrList) =>
    sb.append("link:")
    sb.append(target)
    sb.append('[')
    renderMacroAttrList(attrList, sb)
    sb.append(']')
case CstMailtoMacro(target, attrList) =>
    sb.append("mailto:")
    sb.append(target)
    sb.append('[')
    renderMacroAttrList(attrList, sb)
    sb.append(']')
```

Add a `renderMacroAttrList` helper:

```scala
private def renderMacroAttrList(al: CstMacroAttrList, sb: StringBuilder): Unit =
    renderInlines(al.text, sb)
    if al.hasCaretShorthand then sb.append('^')
    val rest = al.positional.map(identity) ++ al.named.map((k, v) => s"$k=$v")
    if rest.nonEmpty then
        if al.text.nonEmpty || al.hasCaretShorthand then sb.append(',')
        sb.append(rest.mkString(","))
```

**CstVisitor.scala** (lines 127-129) — update children to access `attrList.text`:

```scala
case n: CstUrlMacro    => n.attrList.text
case n: CstLinkMacro   => n.attrList.text
case n: CstMailtoMacro => n.attrList.text
```

**Test files** — search for `CstUrlMacro(`, `CstLinkMacro(`, `CstMailtoMacro(` in all test files. Wrap `text` arguments in `CstMacroAttrList.textOnly(...)`. Key test files:
- `InlineParserSpec.scala`
- `CstLoweringSpec.scala`
- `CstRendererSpec.scala`
- `CstVisitorSpec.scala`

- [ ] **Step 4: Verify compilation**

Run: `./mill __.compile 2>&1 | tail -5`
Expected: SUCCESS

- [ ] **Step 5: Run tests**

Run: `./mill __.testLocal 2>&1 | tail -5`
Expected: All PASS (existing behavior preserved via `textOnly` wrapper)

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(cst): add CstMacroAttrList and migrate link CST nodes

Introduces CstMacroAttrList as a generic attribute container for inline
macro bracket content. CST link nodes now carry attrList instead of
plain text. Existing behavior preserved via CstMacroAttrList.textOnly."
```

---

### Task 2: Add domain types and LinkAttributes to AST

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/ast/Document.scala:64-77`

- [ ] **Step 1: Add opaque types, LinkOption enum, and LinkAttributes**

In `ascribe/src/io/eleven19/ascribe/ast/Document.scala`, before the `Link` case class (line 59), add:

```scala
// ── Link attribute domain types ──────────────────────────────────────────────

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

enum LinkOption derives CanEqual:
    case NoFollow, NoOpener

case class LinkAttributes(
    id: Option[ElementId] = None,
    title: Option[String] = None,
    window: Option[WindowTarget] = None,
    roles: List[CssRole] = Nil,
    options: Set[LinkOption] = Set.empty
) derives CanEqual

object LinkAttributes:
    val empty: LinkAttributes = LinkAttributes()

    /** Matches links that open in a new window/tab. */
    object OpensInNewWindow:
        def unapply(attrs: LinkAttributes): Option[WindowTarget] =
            attrs.window.filter(_ == WindowTarget.Blank)
```

- [ ] **Step 2: Update Link case class to include attributes**

Replace the Link case class (lines 64-77):

```scala
case class Link(variant: LinkVariant, target: String, text: List[Inline], attributes: LinkAttributes)(val span: Span)
    extends Inline derives CanEqual:

    lazy val scheme: Option[String] =
        target.indexOf("://") match
            case -1  => None
            case idx => Some(target.substring(0, idx))

object Link:
    object Scheme:
        def unapply(link: Link): Option[String] = link.scheme
```

- [ ] **Step 3: Fix all compilation errors from the Link signature change**

Every site that constructs or pattern-matches `Link(variant, target, text)` needs the `attributes` parameter. Key files:

**dsl.scala** (lines 33-43) — add `LinkAttributes.empty` to all constructors:

```scala
    def autoLink(target: String): Link =
        Link(LinkVariant.Auto, target, Nil, LinkAttributes.empty)(u)

    def urlLink(scheme: String, target: String, text: Inline*): Link =
        Link(LinkVariant.Macro(MacroKind.Url(scheme)), target, text.toList, LinkAttributes.empty)(u)

    def link(target: String, text: Inline*): Link =
        Link(LinkVariant.Macro(MacroKind.Link), target, text.toList, LinkAttributes.empty)(u)

    def mailtoLink(target: String, text: Inline*): Link =
        Link(LinkVariant.Macro(MacroKind.MailTo), target, text.toList, LinkAttributes.empty)(u)
```

Also add overloads that accept `LinkAttributes`:

```scala
    def link(target: String, attrs: LinkAttributes, text: Inline*): Link =
        Link(LinkVariant.Macro(MacroKind.Link), target, text.toList, attrs)(u)

    def urlLink(scheme: String, target: String, attrs: LinkAttributes, text: Inline*): Link =
        Link(LinkVariant.Macro(MacroKind.Url(scheme)), target, text.toList, attrs)(u)
```

**CstLowering.scala** — update all Link constructions to pass `LinkAttributes.empty` (for now; Task 4 will add real lowering):

```scala
Link(LinkVariant.Auto, target, Nil, LinkAttributes.empty)(inline.span)
```

(And similarly for UrlMacro, LinkMacro, MailtoMacro cases.)

**AstToAsg.scala** (line 384) — update the pattern match. The bridge doesn't use attributes yet, so just match with wildcard:

```scala
case ast.Link(variant, target, text, _) =>
```

**Pipeline files** — update pattern matches in `flattenInlines`, `AsciiDocRenderer.renderInline`, and `itest/AsciiDocParserSteps.inlinesToText`:

```scala
case Link(_, target, text, _) => ...
```

**AstVisitor.scala** — `children` for `Link` (line 128): no change needed since `l.text` still works.

**All test files** — search for `Link(` constructions and pattern matches. Add `LinkAttributes.empty` or `_` wildcard as appropriate.

- [ ] **Step 4: Verify compilation**

Run: `./mill __.compile 2>&1 | tail -5`
Expected: SUCCESS

- [ ] **Step 5: Run tests**

Run: `./mill __.testLocal 2>&1 | tail -5`
Expected: All PASS

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(ast): add LinkAttributes with opaque types to Link node

Adds ElementId, WindowTarget, CssRole opaque types with extractors,
LinkOption enum, and LinkAttributes case class. Link AST node now
carries attributes field (defaults to LinkAttributes.empty)."
```

---

### Task 3: Parser — two-phase bracket attribute parsing

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/parser/InlineParser.scala:178-190`

- [ ] **Step 1: Write failing parser tests**

In `ascribe/test/src/io/eleven19/ascribe/parser/InlineParserSpec.scala`, add a suite:

```scala
        suite("macro attribute parsing")(
            test("plain text without commas/equals is text-only") {
                parse("link:path[simple text]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assertTrue(
                            link.attrList.text == List(CstText("simple text")(u)),
                            link.attrList.named.isEmpty,
                            link.attrList.positional.isEmpty,
                            !link.attrList.hasCaretShorthand
                        )
                    case Failure(msg) => assertTrue(s"$msg" == "")
            },
            test("text with named attribute") {
                parse("link:path[click here,window=_blank]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assertTrue(
                            link.attrList.text == List(CstText("click here")(u)),
                            link.attrList.named == List(("window", "_blank"))
                        )
                    case Failure(msg) => assertTrue(s"$msg" == "")
            },
            test("caret shorthand") {
                parse("link:path[click here^]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assertTrue(
                            link.attrList.text == List(CstText("click here")(u)),
                            link.attrList.hasCaretShorthand
                        )
                    case Failure(msg) => assertTrue(s"$msg" == "")
            },
            test("quoted text with comma") {
                parse("""link:path["text, with comma",role=btn]""") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assertTrue(
                            link.attrList.text == List(CstText("text, with comma")(u)),
                            link.attrList.named == List(("role", "btn"))
                        )
                    case Failure(msg) => assertTrue(s"$msg" == "")
            },
            test("named attributes only, no text") {
                parse("link:path[role=btn,window=_blank]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assertTrue(
                            link.attrList.text.isEmpty,
                            link.attrList.named == List(("role", "btn"), ("window", "_blank"))
                        )
                    case Failure(msg) => assertTrue(s"$msg" == "")
            },
            test("empty brackets") {
                parse("link:path[]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assertTrue(
                            link.attrList.text.isEmpty,
                            link.attrList.named.isEmpty
                        )
                    case Failure(msg) => assertTrue(s"$msg" == "")
            },
            test("mailto with positional params") {
                parse("mailto:user@host[Subscribe,Subscribe me,I want to join]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstMailtoMacro => l }.get
                        assertTrue(
                            link.attrList.text == List(CstText("Subscribe")(u)),
                            link.attrList.positional == List("Subscribe me", "I want to join")
                        )
                    case Failure(msg) => assertTrue(s"$msg" == "")
            },
            test("URL macro with attributes") {
                parse("https://example.com[Example,window=_blank,opts=nofollow]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstUrlMacro => l }.get
                        assertTrue(
                            link.attrList.text == List(CstText("Example")(u)),
                            link.attrList.named == List(("window", "_blank"), ("opts", "nofollow"))
                        )
                    case Failure(msg) => assertTrue(s"$msg" == "")
            }
        ),
```

- [ ] **Step 2: Implement the two-phase bracket parser**

Replace `bracketedInlineContent` (lines 178-190) with a `macroAttrList` parser:

```scala
    /** Characters allowed in raw bracket content (everything except `]` and newlines). */
    private val bracketRawChar: Parsley[Char] = satisfy(c => c != ']' && c != '\n' && c != '\r')

    /** Parses raw content between `[` and `]`, then interprets as attribute list if needed. */
    private val macroAttrList: Parsley[CstMacroAttrList] =
        (pos <~> (char('[') *> manyTill(bracketRawChar, char(']')).map(_.mkString)) <~> pos)
            .map { case ((s, raw), e) => (raw, mkSpan(s, e)) }
            .map { case (raw, span) => parseMacroAttrList(raw, span) }

    /** Interpret raw bracket content as a CstMacroAttrList.
      *
      * If the raw string contains `,` or `=` outside quotes, parse as attribute list.
      * Otherwise treat entire content as first positional (display text).
      */
    private def parseMacroAttrList(raw: String, span: Span): CstMacroAttrList =
        if raw.isEmpty then CstMacroAttrList.empty(span)
        else if !containsAttrSignal(raw) then
            // Simple text — no commas or equals outside quotes
            val (text, hasCaret) = stripTrailingCaret(raw)
            val inlines = parseInlineText(text, span)
            CstMacroAttrList(inlines, Nil, Nil, hasCaret)(span)
        else
            // Attribute list mode — split on commas respecting quotes
            val segments = splitOnCommas(raw)
            val (firstRaw, rest) = (segments.headOption.getOrElse(""), segments.drop(1))
            val (textRaw, hasCaret) = stripTrailingCaret(firstRaw)
            val text = if textRaw.isEmpty then Nil else parseInlineText(unquote(textRaw), span)
            val (positional, named) = rest.foldLeft((List.empty[String], List.empty[(String, String)])) {
                case ((pos, nam), seg) =>
                    seg.indexOf('=') match
                        case -1  => (pos :+ seg.trim, nam)
                        case idx =>
                            val key = seg.substring(0, idx).trim
                            val value = unquote(seg.substring(idx + 1).trim)
                            (pos, nam :+ (key, value))
            }
            CstMacroAttrList(text, positional, named, hasCaret)(span)

    /** Check if raw bracket content contains `,` or `=` outside double quotes. */
    private def containsAttrSignal(raw: String): Boolean =
        var inQuote = false
        var i = 0
        while i < raw.length do
            val c = raw.charAt(i)
            if c == '"' && (i == 0 || raw.charAt(i - 1) != '\\') then inQuote = !inQuote
            else if !inQuote && (c == ',' || c == '=') then return true
            i += 1
        false

    /** Split raw string on commas, respecting double-quoted segments. */
    private def splitOnCommas(raw: String): List[String] =
        val segments = List.newBuilder[String]
        val current = new StringBuilder
        var inQuote = false
        var i = 0
        while i < raw.length do
            val c = raw.charAt(i)
            if c == '"' && (i == 0 || raw.charAt(i - 1) != '\\') then
                inQuote = !inQuote
                current.append(c)
            else if c == ',' && !inQuote then
                segments += current.toString
                current.clear()
            else
                current.append(c)
            i += 1
        segments += current.toString
        segments.result()

    /** Strip trailing `^` from text, returning (text, hasCaret). */
    private def stripTrailingCaret(s: String): (String, Boolean) =
        if s.endsWith("^") then (s.dropRight(1), true)
        else (s, false)

    /** Remove surrounding double quotes and unescape `\"`. */
    private def unquote(s: String): String =
        if s.startsWith("\"") && s.endsWith("\"") then
            s.substring(1, s.length - 1).replace("\\\"", "\"")
        else s

    /** Parse a plain string as inline content (re-uses lineContent parser). */
    private def parseInlineText(s: String, span: Span): List[CstInline] =
        if s.isEmpty then Nil
        else
            lineContent.parse(s) match
                case parsley.Success(inlines) => inlines
                case _                        => List(CstText(s)(span))
```

- [ ] **Step 3: Update link parsers to use macroAttrList**

Replace `bracketedInlineContent` with `macroAttrList` in each link parser:

For `linkMacro`:
```scala
    val linkMacro: Parsley[CstInline] =
        atomic(
            (pos <~> (string("link:") *> macroTargetChars) <~> macroAttrList <~> pos)
                .map { case (((s, target), attrList), e) =>
                    CstLinkMacro(target, attrList)(mkSpan(s, e))
                }
        ).flatMap(node => lastChar.set(Some(']')) *> pure(node: CstInline))
            .label("link macro")
            .explain("Link macro syntax: link:target[text]")
```

For `mailtoMacro`:
```scala
    val mailtoMacro: Parsley[CstInline] =
        atomic(
            (pos <~> (string("mailto:") *> macroTargetChars) <~> macroAttrList <~> pos)
                .map { case (((s, target), attrList), e) =>
                    CstMailtoMacro(target, attrList)(mkSpan(s, e))
                }
        ).flatMap(node => lastChar.set(Some(']')) *> pure(node: CstInline))
            .label("mailto macro")
            .explain("Mailto macro syntax: mailto:address[text]")
```

For `urlMacro`:
```scala
    val urlMacro: Parsley[CstInline] =
        atomic(
            (pos <~> (urlScheme <~> urlChars).map(_ + _) <~> macroAttrList <~> pos)
                .map { case (((s, target), attrList), e) =>
                    CstUrlMacro(target, attrList)(mkSpan(s, e))
                }
        ).flatMap(node => lastChar.set(Some(']')) *> pure(node: CstInline))
            .label("URL macro")
```

- [ ] **Step 4: Run tests**

Run: `./mill ascribe.test.testLocal 2>&1 | tail -5`
Expected: All PASS including new attribute parsing tests

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(parser): two-phase bracket parser for macro attribute lists

Replaces bracketedInlineContent with macroAttrList. Detects commas/
equals outside quotes to switch between text-only and attribute-list
modes. Supports quoted text, caret shorthand, positional and named
attributes."
```

---

### Task 4: Lowering — interpret CstMacroAttrList into LinkAttributes

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstLowering.scala:55-65`

- [ ] **Step 1: Write failing lowering tests**

In `ascribe/test/src/io/eleven19/ascribe/cst/CstLoweringSpec.scala`, add a suite:

```scala
        suite("link attribute lowering")(
            test("caret shorthand produces window=_blank and NoOpener") {
                val attrList = CstMacroAttrList(
                    List(CstText("click")(u)), Nil, Nil, hasCaretShorthand = true
                )(u)
                val cst = CstDocument(None, List(CstParagraph(List(
                    CstParagraphLine(List(CstLinkMacro("path", attrList)(u)))(u)
                ))(u)))(u)
                val result = CstLowering.toAst(cst)
                val linkNode = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Link]
                assertTrue(
                    linkNode.attributes.window == Some(WindowTarget.Blank),
                    linkNode.attributes.options.contains(LinkOption.NoOpener)
                )
            },
            test("window=_blank named attr produces window and NoOpener") {
                val attrList = CstMacroAttrList(
                    List(CstText("click")(u)), Nil, List(("window", "_blank")), false
                )(u)
                val cst = CstDocument(None, List(CstParagraph(List(
                    CstParagraphLine(List(CstLinkMacro("path", attrList)(u)))(u)
                ))(u)))(u)
                val result = CstLowering.toAst(cst)
                val linkNode = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Link]
                assertTrue(
                    linkNode.attributes.window == Some(WindowTarget.Blank),
                    linkNode.attributes.options.contains(LinkOption.NoOpener)
                )
            },
            test("role attr produces CssRole") {
                val attrList = CstMacroAttrList(
                    List(CstText("click")(u)), Nil, List(("role", "btn")), false
                )(u)
                val cst = CstDocument(None, List(CstParagraph(List(
                    CstParagraphLine(List(CstLinkMacro("path", attrList)(u)))(u)
                ))(u)))(u)
                val result = CstLowering.toAst(cst)
                val linkNode = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Link]
                assertTrue(linkNode.attributes.roles == List(CssRole("btn")))
            },
            test("opts=nofollow produces NoFollow") {
                val attrList = CstMacroAttrList(
                    List(CstText("click")(u)), Nil, List(("opts", "nofollow")), false
                )(u)
                val cst = CstDocument(None, List(CstParagraph(List(
                    CstParagraphLine(List(CstLinkMacro("path", attrList)(u)))(u)
                ))(u)))(u)
                val result = CstLowering.toAst(cst)
                val linkNode = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Link]
                assertTrue(linkNode.attributes.options.contains(LinkOption.NoFollow))
            },
            test("empty attrs produce LinkAttributes.empty") {
                val attrList = CstMacroAttrList(Nil, Nil, Nil, false)(u)
                val cst = CstDocument(None, List(CstParagraph(List(
                    CstParagraphLine(List(CstLinkMacro("path", attrList)(u)))(u)
                ))(u)))(u)
                val result = CstLowering.toAst(cst)
                val linkNode = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Link]
                assertTrue(linkNode.attributes == LinkAttributes.empty)
            }
        ),
```

- [ ] **Step 2: Implement attribute interpretation in lowering**

In `ascribe/src/io/eleven19/ascribe/cst/CstLowering.scala`, add a helper function and update the link lowering cases:

```scala
        def lowerMacroAttrs(attrList: CstMacroAttrList): LinkAttributes =
            var la = LinkAttributes.empty
            // Process caret shorthand
            if attrList.hasCaretShorthand then
                la = la.copy(window = Some(WindowTarget.Blank), options = la.options + LinkOption.NoOpener)
            // Process named attributes
            attrList.named.foreach { (key, value) =>
                key match
                    case "window" =>
                        val wt = WindowTarget(value)
                        la = la.copy(window = Some(wt))
                        if wt == WindowTarget.Blank then la = la.copy(options = la.options + LinkOption.NoOpener)
                    case "role"  => la = la.copy(roles = la.roles :+ CssRole(value))
                    case "id"    => la = la.copy(id = Some(ElementId(value)))
                    case "title" => la = la.copy(title = Some(value))
                    case "opts"  =>
                        value.split("[,\\s]+").foreach {
                            case "nofollow" => la = la.copy(options = la.options + LinkOption.NoFollow)
                            case "noopener" => la = la.copy(options = la.options + LinkOption.NoOpener)
                            case _          => () // ignore unknown options
                        }
                    case _ => () // ignore unknown named attrs
            }
            la

        // Update link lowering cases:
        case CstUrlMacro(target, attrList) =>
            val scheme = target.indexOf("://") match
                case -1  => ""
                case idx => target.substring(0, idx)
            Link(LinkVariant.Macro(MacroKind.Url(scheme)), target, lowerInlines(attrList.text), lowerMacroAttrs(attrList))(inline.span)
        case CstLinkMacro(target, attrList) =>
            Link(LinkVariant.Macro(MacroKind.Link), target, lowerInlines(attrList.text), lowerMacroAttrs(attrList))(inline.span)
        case CstMailtoMacro(target, attrList) =>
            Link(LinkVariant.Macro(MacroKind.MailTo), target, lowerInlines(attrList.text), lowerMacroAttrs(attrList))(inline.span)
```

- [ ] **Step 3: Run tests**

Run: `./mill ascribe.test.testLocal 2>&1 | tail -5`
Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(cst): lower CstMacroAttrList to domain-typed LinkAttributes

Interprets caret shorthand, window, role, opts, id, title attributes.
window=_blank implicitly adds NoOpener. opts supports comma-separated
values."
```

---

### Task 5: ASG bridge and pipeline updates for LinkAttributes

**Files:**
- Modify: `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala:384-393`
- Modify: `ascribe/pipeline/src/io/eleven19/ascribe/pipeline/AsciiDocRenderer.scala:163-169`

- [ ] **Step 1: Update ASG bridge**

In `AstToAsg.scala`, the bridge currently ignores attributes (via `_` wildcard from Task 2). For now this is acceptable — the ASG `Ref` type doesn't have an attributes field. No change needed beyond what Task 2 already did.

- [ ] **Step 2: Update AsciiDocRenderer to render attributes**

In `ascribe/pipeline/src/io/eleven19/ascribe/pipeline/AsciiDocRenderer.scala`, update the Link rendering cases to include attributes:

```scala
case Link(LinkVariant.Macro(MacroKind.Link), target, text, attrs) =>
    val attrStr = renderLinkAttrs(text, attrs)
    s"link:$target[$attrStr]"
case Link(LinkVariant.Macro(MacroKind.MailTo), target, text, attrs) =>
    val attrStr = renderLinkAttrs(text, attrs)
    s"mailto:$target[$attrStr]"
case Link(LinkVariant.Macro(MacroKind.Url(_)), target, text, attrs) =>
    val attrStr = renderLinkAttrs(text, attrs)
    s"$target[$attrStr]"
```

Add helper:

```scala
private def renderLinkAttrs(text: List[Inline], attrs: LinkAttributes): String =
    val parts = List.newBuilder[String]
    if text.nonEmpty then parts += renderInlines(text)
    if attrs.window.contains(WindowTarget.Blank) && attrs.roles.isEmpty && attrs.options == Set(LinkOption.NoOpener) then
        // Can use ^ shorthand
        val textStr = if text.nonEmpty then renderInlines(text) else ""
        return s"$textStr^"
    attrs.id.foreach(id => parts += s"id=$id")
    attrs.roles.foreach(r => parts += s"role=$r")
    attrs.title.foreach(t => parts += s"title=$t")
    attrs.window.foreach(w => parts += s"window=$w")
    if attrs.options.nonEmpty then
        val optStrs = attrs.options.map {
            case LinkOption.NoFollow => "nofollow"
            case LinkOption.NoOpener => "noopener"
        }
        parts += s"opts=${optStrs.mkString(",")}"
    parts.result().mkString(",")
```

- [ ] **Step 3: Run all tests**

Run: `./mill __.testLocal 2>&1 | tail -5`
Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(pipeline): render LinkAttributes in AsciiDocRenderer"
```

---

### Task 6: Formatting and final verification

- [ ] **Step 1: Run formatting**

Run: `./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources 2>&1 | tail -3`

- [ ] **Step 2: Run full test suite**

Run: `./mill __.testLocal 2>&1 | grep -E "FAILED|SUCCESS" | tail -5`
Expected: All SUCCESS

- [ ] **Step 3: Commit formatting**

```bash
git add -A && git diff --cached --stat && git commit -m "style: apply scalafmt formatting" || echo "nothing to format"
```

- [ ] **Step 4: Verify clean state**

Run: `git status`
Expected: clean working tree
