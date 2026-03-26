# Attribute References and Admonition Blocks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `{attr-name}` attribute reference substitution and `NOTE: text` / `[NOTE]\n====` admonition paragraph support to the ascribe AsciiDoc parser.

**Architecture:** Two independent features share the same pipeline layers (CST → AST → ASG). Feature 1 introduces an `AttributeMap` opaque type and resolves `{name}` refs during CST lowering using a `var` closure over local `def`s. Feature 2 adds a `CstAdmonitionParagraph` CST node, a new `ast.Admonition` block, and plumbs it through to the ASG bridge and AST renderer; the delimited `[NOTE]\n====` form already works end-to-end.

**Tech Stack:** Scala 3, Parsley parser combinators, ZIO Test, Mill build tool. All tests use `ZIOSpecDefault`. Run tests with `./mill ascribe.test.testOnly 'io.eleven19.ascribe.<module>.<SpecName>'`. Run all tests with `./mill ascribe.test` (and `./mill ascribe.bridge.test`, `./mill ascribe.pipeline.test`).

**Working directory:** All paths are relative to `.worktrees/feat-adoc-gaps/`.

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `ascribe/src/io/eleven19/ascribe/lexer/AsciiDocLexer.scala` | Modify | Exclude `{`/`}` from content chars; add to unpaired markup |
| `ascribe/src/io/eleven19/ascribe/cst/CstNodes.scala` | Modify | Add `CstAttributeRef`; add `CstAdmonitionParagraph`; add `unset` to `CstAttributeEntry` |
| `ascribe/src/io/eleven19/ascribe/parser/InlineParser.scala` | Modify | Add `attrRefInline`; update `inlineElement` order |
| `ascribe/src/io/eleven19/ascribe/parser/BlockParser.scala` | Modify | Extend `attributeEntryBlock` for `:!name:`; add `admonitionParagraphBlock` |
| `ascribe/src/io/eleven19/ascribe/cst/CstLowering.scala` | Modify | Add `AttributeMap`; refactor to closure-based local defs; admonition lowering |
| `ascribe/src/io/eleven19/ascribe/cst/CstRenderer.scala` | Modify | Add cases for `CstAttributeRef`, `CstAdmonitionParagraph`, unset entry |
| `ascribe/src/io/eleven19/ascribe/cst/CstVisitor.scala` | Modify | Add dispatch + children for both new CST nodes |
| `ascribe/src/io/eleven19/ascribe/ast/Document.scala` | Modify | Add `AdmonitionKind` enum + `Admonition` block |
| `ascribe/src/io/eleven19/ascribe/ast/AstVisitor.scala` | Modify | Add `visitAdmonition` method, dispatch arm, children case |
| `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala` | Modify | Convert `ast.Admonition` → `asg.Admonition` (paragraph form) |
| `ascribe/pipeline/src/io/eleven19/ascribe/pipeline/AsciiDocRenderer.scala` | Modify | Add `Admonition` render case |
| `ascribe/test/src/io/eleven19/ascribe/parser/InlineParserSpec.scala` | Modify | Tests for `{name}` parsing |
| `ascribe/test/src/io/eleven19/ascribe/parser/BlockParserSpec.scala` | **Create** | Tests for `:!name:` and `NOTE: text` parsing |
| `ascribe/test/src/io/eleven19/ascribe/cst/CstLoweringSpec.scala` | Modify | Tests for attr resolution and admonition lowering |
| `ascribe/test/src/io/eleven19/ascribe/cst/CstRendererSpec.scala` | Modify | Roundtrip tests for new CST nodes |
| `ascribe/test/src/io/eleven19/ascribe/cst/CstVisitorSpec.scala` | Modify | Traversal tests for new CST nodes |
| `ascribe/test/src/io/eleven19/ascribe/ast/AstVisitorSpec.scala` | Modify | Dispatch + children tests for `Admonition` |
| `ascribe/bridge/test/src/io/eleven19/ascribe/bridge/AstToAsgSpec.scala` | Modify | Bridge conversion test for paragraph admonition |
| `ascribe/pipeline/test/src/io/eleven19/ascribe/pipeline/AsciiDocRendererSpec.scala` | Modify | Render test for `Admonition` |

---

## Task 1: Lexer — exclude `{` and `}` from content chars

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/lexer/AsciiDocLexer.scala`
- Test: `ascribe/test/src/io/eleven19/ascribe/parser/InlineParserSpec.scala`

- [ ] **Step 1: Write failing test for `{` in inline content**

  Add to `InlineParserSpec` inside a new `suite("attribute refs")`:

  ```scala
  suite("attribute refs")(
      test("bare {name} is not swallowed as plain text") {
          parse("{version}") match
              case Success(inlines) =>
                  // Currently parses as CstText("{version}") — after lexer fix it will not match plainText
                  // This test verifies {version} is no longer a single CstText chunk
                  assertTrue(inlines != List(CstText("{version}")(u)))
              case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
      }
  )
  ```

- [ ] **Step 2: Run test to confirm it currently fails (i.e. `{version}` IS plain text now)**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.parser.InlineParserSpec'
  ```
  Expected: test fails because `{version}` currently parses as `CstText("{version}")(u)`.

- [ ] **Step 3: Update `isContentChar` in `AsciiDocLexer.scala`**

  In `AsciiDocLexer.scala`, find `isContentChar` and add `{` and `}` to the excluded set:

  ```scala
  def isContentChar(c: Char): Boolean =
      !c.isControl && c != '\n' && c != '\r' &&
          c != '*' && c != '_' && c != '`' &&
          c != '{' && c != '}'
  ```

  Also add `{` and `}` to `unpairedMarkupChar`:
  ```scala
  val unpairedMarkupChar: Parsley[Char] =
      satisfy(c => (c == '*' || c == '_' || c == '`' || c == '{' || c == '}') && c != '\n')
  ```

- [ ] **Step 4: Run inline parser tests — expect existing tests still pass**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.parser.InlineParserSpec'
  ```
  Expected: all existing tests pass; `{version}` now splits into separate tokens (test still shows the tokens).

- [ ] **Step 5: Commit**

  ```bash
  git add ascribe/src/io/eleven19/ascribe/lexer/AsciiDocLexer.scala \
          ascribe/test/src/io/eleven19/ascribe/parser/InlineParserSpec.scala
  git commit -m "feat(lexer): exclude { and } from content chars for attribute ref parsing"
  ```

---

## Task 2: CST — add `CstAttributeRef` and update `CstAttributeEntry`

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstNodes.scala`

- [ ] **Step 1: Add `CstAttributeRef` inline node**

  In `CstNodes.scala`, add after `CstMono`:
  ```scala
  case class CstAttributeRef(name: String)(val span: Span) extends CstInline derives CanEqual
  ```

- [ ] **Step 2: Add `unset: Boolean` to `CstAttributeEntry`**

  Change:
  ```scala
  case class CstAttributeEntry(
      name: String,
      value: String
  )(val span: Span)
      extends CstBlock derives CanEqual
  ```
  To:
  ```scala
  case class CstAttributeEntry(
      name: String,
      value: String,
      unset: Boolean
  )(val span: Span)
      extends CstBlock derives CanEqual
  ```

- [ ] **Step 3: Compile to surface all match sites broken by the field addition**

  ```bash
  ./mill ascribe.compile 2>&1 | grep -i "error\|warning" | head -30
  ```
  Expected: compiler errors for any exhaustive match on `CstAttributeEntry`. (Most matches use wildcards so typically no errors — but verify.)

- [ ] **Step 4: Write failing renderer test for unset entry**

  Add to `CstRendererSpec`:
  ```scala
  test("render produces :!name: for unset entry") {
      Ascribe.parseCst(":!my-attr:\n") match
          case Success(cst) =>
              val rendered = CstRenderer.render(cst)
              assertTrue(rendered.contains(":!my-attr:"))
          case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
  }
  ```

  Run to confirm it fails:
  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.cst.CstRendererSpec'
  ```

- [ ] **Step 5: Fix `CstRenderer.renderAttributeEntry` to handle `unset`**

  In `CstRenderer.scala`, find the method that renders `CstAttributeEntry`. Update it to emit `:!name:` when `unset = true`:

  ```scala
  private def renderAttributeEntry(e: CstAttributeEntry, sb: StringBuilder): Unit =
      if e.unset then
          sb.append(':').append('!').append(e.name).append(':').append('\n')
      else
          sb.append(':').append(e.name).append(':')
          if e.value.nonEmpty then sb.append(' ').append(e.value)
          sb.append('\n')
  ```

- [ ] **Step 6: Fix existing `CstAttributeEntry` construction sites to pass `unset = false`**

  In `BlockParser.scala`, find `CstAttributeEntry(name, value)(...)` and add the third arg:
  ```scala
  CstAttributeEntry(name, value, unset = false)(mkSpan(s, e))
  ```

  In `CstLowering.scala` (and anywhere else that constructs `CstAttributeEntry`), do the same.

- [ ] **Step 7: Compile clean**

  ```bash
  ./mill ascribe.compile 2>&1 | grep -c "error"
  ```
  Expected: 0 errors.

- [ ] **Step 8: Run all ascribe tests to confirm no regressions (including new renderer test)**

  ```bash
  ./mill ascribe.test
  ```
  Expected: all pass, including the `:!my-attr:` renderer test added in Step 4.

- [ ] **Step 9: Commit**

  ```bash
  git add ascribe/src/io/eleven19/ascribe/cst/CstNodes.scala \
          ascribe/src/io/eleven19/ascribe/parser/BlockParser.scala \
          ascribe/src/io/eleven19/ascribe/cst/CstRenderer.scala \
          ascribe/test/src/io/eleven19/ascribe/cst/CstRendererSpec.scala
  git commit -m "feat(cst): add CstAttributeRef inline node and unset field to CstAttributeEntry"
  ```

---

## Task 3: Inline parser — `attrRefInline`

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/parser/InlineParser.scala`
- Modify: `ascribe/test/src/io/eleven19/ascribe/parser/InlineParserSpec.scala`

- [ ] **Step 1: Write failing tests**

  Add to the `suite("attribute refs")` started in Task 1:

  ```scala
  test("parses {name} as CstAttributeRef") {
      parse("{version}") match
          case Success(inlines) =>
              assertTrue(inlines == List(CstAttributeRef("version")(u)))
          case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
  },
  test("parses attribute ref embedded in text") {
      parse("Release {version} now") match
          case Success(inlines) =>
              assertTrue(inlines == List(
                  CstText("Release ")(u),
                  CstAttributeRef("version")(u),
                  CstText(" now")(u)
              ))
          case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
  },
  test("attribute ref name must start with letter") {
      parse("{1foo}") match
          case Success(inlines) =>
              // {1foo} is not a valid attr ref name — parses as plain { text
              assertTrue(!inlines.exists { case CstAttributeRef(_) => true; case _ => false })
          case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
  },
  test("lone { is plain text fallback") {
      parse("a{b") match
          case Success(inlines) =>
              val texts = inlines.collect { case CstText(c) => c }
              assertTrue(texts.contains("{"))
          case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.parser.InlineParserSpec'
  ```

- [ ] **Step 3: Add `attrRefInline` parser to `InlineParser.scala`**

  Add import at top: `import io.eleven19.ascribe.cst.CstAttributeRef`

  Add the parser (after `monoSpan`):
  ```scala
  /** Parses an attribute reference: `{name}` where name starts with a letter. */
  val attrRefInline: Parsley[CstInline] =
      atomic(
          (pos <~>
              (char('{') *>
                  (satisfy(_.isLetter) <~> stringOfMany(satisfy(c => c.isLetterOrDigit || c == '-' || c == '_')))
                      .map { case (first, rest) => first.toString + rest } <*
                  char('}')) <~>
              pos)
              .map { case ((s, name), e) => CstAttributeRef(name)(mkSpan(s, e)) }
      ).label("attribute reference")
  ```

  Update `inlineElement` to include `attrRefInline` after `monoSpan`:
  ```scala
  val inlineElement: Parsley[CstInline] =
      boldSpan | constrainedBoldSpan | italicSpan | monoSpan |
          attrRefInline | plainTextInline | unpairedMarkupInline
  ```

  Add `CstAttributeRef` to the import in `InlineParser.scala`.

- [ ] **Step 4: Run tests to confirm they pass**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.parser.InlineParserSpec'
  ```
  Expected: all pass.

- [ ] **Step 5: Commit**

  ```bash
  git add ascribe/src/io/eleven19/ascribe/parser/InlineParser.scala \
          ascribe/test/src/io/eleven19/ascribe/parser/InlineParserSpec.scala
  git commit -m "feat(parser): add attrRefInline parser for {name} attribute references"
  ```

---

## Task 4: Block parser — `:!name:` unset form

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/parser/BlockParser.scala`
- Create: `ascribe/test/src/io/eleven19/ascribe/parser/BlockParserSpec.scala`

- [ ] **Step 1: Create `BlockParserSpec.scala` with failing test**

  ```scala
  package io.eleven19.ascribe.parser

  import parsley.{Failure, Success}
  import zio.test.*

  import io.eleven19.ascribe.ast.Span
  import io.eleven19.ascribe.cst.{CstAttributeEntry, CstBlock}
  import io.eleven19.ascribe.parser.BlockParser.attributeEntryBlock

  object BlockParserSpec extends ZIOSpecDefault:
      private def parse(input: String) = attributeEntryBlock.parse(input)

      def spec = suite("BlockParser")(
          suite("attributeEntryBlock")(
              test("parses normal attribute entry") {
                  parse(":my-attr: some value\n") match
                      case Success(CstAttributeEntry("my-attr", "some value", false)) => assertTrue(true)
                      case Success(other) => assertTrue(s"unexpected: $other" == "")
                      case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
              },
              test("parses unset attribute entry :!name:") {
                  parse(":!my-attr:\n") match
                      case Success(CstAttributeEntry("my-attr", "", true)) => assertTrue(true)
                      case Success(other) => assertTrue(s"unexpected: $other" == "")
                      case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
              },
              test("parses empty-value attribute entry") {
                  parse(":my-attr:\n") match
                      case Success(CstAttributeEntry("my-attr", "", false)) => assertTrue(true)
                      case Success(other) => assertTrue(s"unexpected: $other" == "")
                      case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
              }
          )
      )
  ```

- [ ] **Step 2: Run test to confirm it fails**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.parser.BlockParserSpec'
  ```
  Expected: unset test fails.

- [ ] **Step 3: Update `attributeEntryBlock` in `BlockParser.scala` to handle `:!name:`**

  The current parser starts with `char(':') *> stringOfSome(...)`. Add a branch that consumes an optional `!` after the opening `:`:

  ```scala
  val attributeEntryBlock: Parsley[CstBlock] =
      atomic(
          (pos <~>
              (char(':') *>
                  option(char('!')).map(_.isDefined) <~>
                  stringOfSome(satisfy(c => c != ':' && c != '\n' && c != '\r')) <* char(':') <* option(char(' '))) <~>
              many(nonEolChar).map(_.mkString) <~> pos <* eolOrEof)
              .map { case ((((s, unset), name), value), e) =>
                  CstAttributeEntry(name, if unset then "" else value, unset)(mkSpan(s, e))
              }
      ).label("attribute entry")
  ```

- [ ] **Step 4: Run tests to confirm all pass**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.parser.BlockParserSpec'
  ```

- [ ] **Step 5: Run full test suite to confirm no regressions**

  ```bash
  ./mill ascribe.test
  ```

- [ ] **Step 6: Commit**

  ```bash
  git add ascribe/src/io/eleven19/ascribe/parser/BlockParser.scala \
          ascribe/test/src/io/eleven19/ascribe/parser/BlockParserSpec.scala
  git commit -m "feat(parser): support :!name: attribute unset form in block parser"
  ```

---

## Task 5: `AttributeMap` type + lowering refactor

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstLowering.scala`
- Modify: `ascribe/test/src/io/eleven19/ascribe/cst/CstLoweringSpec.scala`

- [ ] **Step 1: Write failing tests for attribute resolution**

  Add to `CstLoweringSpec`:

  ```scala
  suite("attribute references")(
      test("resolves attribute ref defined in header") {
          val entry = CstAttributeEntry("version", "1.0", false)(u)
          val ref   = CstAttributeRef("version")(u)
          val cst   = CstDocument(
              Some(CstDocumentHeader(
                  CstHeading(1, "=", List(CstText("Doc")(u)))(u),
                  List(entry)
              )(u)),
              List(CstParagraph(List(CstParagraphLine(List(ref))(u)))(u))
          )(u)
          assertTrue(CstLowering.toAst(cst) == document(paragraph(text("1.0"))))
      },
      test("resolves attribute ref defined in body") {
          val entry = CstAttributeEntry("foo", "bar", false)(u)
          val ref   = CstAttributeRef("foo")(u)
          val cst = CstDocument(None, List(
              entry,
              CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
          ))(u)
          assertTrue(CstLowering.toAst(cst) == document(paragraph(text("bar"))))
      },
      test("unresolved attribute ref passes through as {name}") {
          val ref = CstAttributeRef("unknown")(u)
          val cst = CstDocument(None, List(
              CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
          ))(u)
          assertTrue(CstLowering.toAst(cst) == document(paragraph(text("{unknown}"))))
      },
      test("unset body entry removes attribute from scope") {
          val set   = CstAttributeEntry("foo", "bar", false)(u)
          val unset = CstAttributeEntry("foo", "", true)(u)
          val ref   = CstAttributeRef("foo")(u)
          val cst = CstDocument(None, List(
              set, unset,
              CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
          ))(u)
          assertTrue(CstLowering.toAst(cst) == document(paragraph(text("{foo}"))))
      },
      test("built-in {empty} resolves to empty string") {
          val ref = CstAttributeRef("empty")(u)
          val cst = CstDocument(None, List(
              CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
          ))(u)
          assertTrue(CstLowering.toAst(cst) == document(paragraph(text(""))))
      },
      test("built-in {sp} resolves to space") {
          val ref = CstAttributeRef("sp")(u)
          val cst = CstDocument(None, List(
              CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
          ))(u)
          assertTrue(CstLowering.toAst(cst) == document(paragraph(text(" "))))
      },
      test("attribute ref in heading title is resolved") {
          val entry = CstAttributeEntry("title-word", "World", false)(u)
          val ref   = CstAttributeRef("title-word")(u)
          val cst = CstDocument(None, List(
              entry,
              CstHeading(2, "==", List(CstText("Hello ")(u), ref))(u)
          ))(u)
          val doc = CstLowering.toAst(cst)
          val headingTexts = doc.blocks.flatMap {
              case ast.Heading(_, title) => title.collect { case ast.Text(c) => c }
              case _                      => Nil
          }
          assertTrue(headingTexts.contains("World"))
      }
  )
  ```

  Add import: `import io.eleven19.ascribe.cst.{CstAttributeEntry, CstAttributeRef, ...}` (extend existing import)
  Add import: `import io.eleven19.ascribe.ast as ast`

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.cst.CstLoweringSpec'
  ```

- [ ] **Step 3: Add `AttributeMap` opaque type to `CstLowering.scala`**

  Add before the `object CstLowering`:

  ```scala
  opaque type AttributeMap = Map[String, String]

  object AttributeMap:
      val builtIns: AttributeMap =
          Map("empty" -> "", "sp" -> " ", "nbsp" -> "\u00A0", "zwsp" -> "\u200B")

      def fromHeader(entries: List[CstAttributeEntry]): AttributeMap =
          entries
              .filterNot(_.unset)    // header-level unsets are a known limitation; skip them
              .foldLeft(builtIns)((m, e) => m + (e.name -> e.value))

      extension (m: AttributeMap)
          def set(name: String, value: String): AttributeMap = m + (name -> value)
          def unset(name: String): AttributeMap              = m - name
          def resolve(name: String): String                  = m.getOrElse(name, s"{$name}")
  ```

- [ ] **Step 4: Refactor `toAst` to use local defs and `AttributeMap`**

  Convert `lowerInline`, `lowerInlines`, and `lowerBlock` to local `def`s inside `toAst` that close over `var attrs`. This means all recursive calls within nested delimited block lowering automatically see the same map without any signature changes.

  Copy the bodies of the existing private `lowerBlock`, `lowerInlines`, and `lowerInline` methods into local defs. **Do not add a `CstAdmonitionParagraph` case yet** — add a stub `case _: CstAdmonitionParagraph => None` to keep the match exhaustive; the real implementation comes in Task 10.

  For `private[cst] def lowerInlines` (called by `CstLoweringSpec` tests directly): **delete it** and update those test usages to construct a full `CstDocument` and call `toAst` instead. This is simpler than maintaining a detached helper.

  ```scala
  def toAst(cst: CstDocument): Document =
      val header = cst.header.map(lowerHeader)
      var attrs  = AttributeMap.fromHeader(cst.header.toList.flatMap(_.attributes))

      def lowerInline(inline: CstInline): Inline = inline match
          case CstText(content)        => Text(content)(inline.span)
          case CstBold(content, false) => Bold(lowerInlines(content))(inline.span)
          case CstBold(content, true)  => ConstrainedBold(lowerInlines(content))(inline.span)
          case CstItalic(content)      => Italic(lowerInlines(content))(inline.span)
          case CstMono(content)        => Mono(lowerInlines(content))(inline.span)
          case CstAttributeRef(name)   => Text(attrs.resolve(name))(inline.span)

      def lowerInlines(inlines: List[CstInline]): List[Inline] = inlines.map(lowerInline)

      def lowerBlock(block: CstBlock): Option[Block] = block match
          // ... copy all existing cases verbatim, replacing private method calls
          //     with the local defs ...
          case _: CstAdmonitionParagraph => None   // stub; replaced in Task 10

      val blocks = cst.content
          .collect { case b: CstBlock => b }
          .flatMap {
              case CstAttributeEntry(name, value, false) => attrs = attrs.set(name, value); None
              case CstAttributeEntry(name, _, true)      => attrs = attrs.unset(name); None
              case other                                 => lowerBlock(other)
          }
      Document(header, restructure(blocks))(cst.span)
  ```

  After this refactor, delete the old private `lowerBlock`, `lowerInlines`, and `lowerInline` methods from `CstLowering` (they are now local defs and the private versions are dead code).

- [ ] **Step 5: Run lowering tests**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.cst.CstLoweringSpec'
  ```
  Expected: all new tests pass, all existing tests pass.

- [ ] **Step 6: Run full suite**

  ```bash
  ./mill ascribe.test
  ```

- [ ] **Step 7: Commit**

  ```bash
  git add ascribe/src/io/eleven19/ascribe/cst/CstLowering.scala \
          ascribe/test/src/io/eleven19/ascribe/cst/CstLoweringSpec.scala
  git commit -m "feat(lowering): add AttributeMap type and resolve {attr-name} references during lowering"
  ```

---

## Task 6: CST Visitor — new nodes

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstVisitor.scala`
- Modify: `ascribe/test/src/io/eleven19/ascribe/cst/CstVisitorSpec.scala`

- [ ] **Step 1: Write failing tests**

  Add to `CstVisitorSpec`:

  ```scala
  test("collect finds CstAttributeRef nodes") {
      Ascribe.parseCst("{version} text\n") match
          case Success(doc) =>
              val refs = doc.collect { case r: CstAttributeRef => r.name }
              assertTrue(refs == List("version"))
          case _ => assertTrue(false)
  },
  test("count includes CstAttributeRef in total") {
      Ascribe.parseCst("{x}\n") match
          case Success(doc) =>
              assertTrue(doc.count > 3)  // document + paragraph + line + ref
          case _ => assertTrue(false)
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.cst.CstVisitorSpec'
  ```

- [ ] **Step 3: Add `CstAttributeRef` to `CstVisitor`**

  In `CstVisitor` trait, add:
  ```scala
  def visitAttributeRef(node: CstAttributeRef): A = visitInline(node)
  ```

  In `CstVisitor.visit`, add:
  ```scala
  case n: CstAttributeRef => visitor.visitAttributeRef(n)
  ```

  In `CstVisitor.children`, add:
  ```scala
  case _: CstAttributeRef => Nil
  ```

- [ ] **Step 4: Run tests to confirm they pass**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.cst.CstVisitorSpec'
  ```

- [ ] **Step 5: Commit**

  ```bash
  git add ascribe/src/io/eleven19/ascribe/cst/CstVisitor.scala \
          ascribe/test/src/io/eleven19/ascribe/cst/CstVisitorSpec.scala
  git commit -m "feat(cst): add CstAttributeRef dispatch and children to CstVisitor"
  ```

---

## Task 7: CST Renderer — new nodes

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstRenderer.scala`
- Modify: `ascribe/test/src/io/eleven19/ascribe/cst/CstRendererSpec.scala`

- [ ] **Step 1: Write failing tests**

  Add to `CstRendererSpec`:

  ```scala
  test("attribute ref roundtrips") {
      roundtrip("{version} text\n")
  },
  test("unset attribute entry roundtrips") {
      roundtrip(":!my-attr:\n")
  },
  test("render produces correct text for attribute ref") {
      Ascribe.parseCst("Release {version}.\n") match
          case Success(cst) =>
              val rendered = CstRenderer.render(cst)
              assertTrue(rendered.contains("{version}"))
          case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
  },
  test("render produces :!name: for unset entry") {
      Ascribe.parseCst(":!my-attr:\n") match
          case Success(cst) =>
              val rendered = CstRenderer.render(cst)
              assertTrue(rendered.contains(":!my-attr:"))
          case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.cst.CstRendererSpec'
  ```

- [ ] **Step 3: Add `CstAttributeRef` case to `renderInline` in `CstRenderer.scala`**

  Find the `renderInlines` / `renderInline` helper (or the inline rendering section in `renderBlock`) and add:

  ```scala
  case ref: CstAttributeRef =>
      sb.append('{').append(ref.name).append('}')
  ```

- [ ] **Step 4: Run tests to confirm they pass**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.cst.CstRendererSpec'
  ```

- [ ] **Step 5: Commit**

  ```bash
  git add ascribe/src/io/eleven19/ascribe/cst/CstRenderer.scala \
          ascribe/test/src/io/eleven19/ascribe/cst/CstRendererSpec.scala
  git commit -m "feat(cst): render CstAttributeRef and unset CstAttributeEntry in CstRenderer"
  ```

---

## Task 8: Admonition paragraph — CST node + block parser

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstNodes.scala`
- Modify: `ascribe/src/io/eleven19/ascribe/parser/BlockParser.scala`
- Modify: `ascribe/test/src/io/eleven19/ascribe/parser/BlockParserSpec.scala`

- [ ] **Step 1: Add `CstAdmonitionParagraph` to `CstNodes.scala`**

  After `CstAttributeEntry`:
  ```scala
  /** Paragraph-form admonition: `NOTE: text on same line` */
  case class CstAdmonitionParagraph(
      kind: String,
      content: List[CstInline]
  )(val span: Span)
      extends CstBlock derives CanEqual
  ```

- [ ] **Step 2: Write failing block parser tests**

  Add to `BlockParserSpec`:

  ```scala
  suite("admonitionParagraphBlock")(
      test("parses NOTE: paragraph") {
          BlockParser.admonitionParagraphBlock.parse("NOTE: Watch out.\n") match
              case Success(CstAdmonitionParagraph("NOTE", content)) =>
                  assertTrue(content.nonEmpty)
              case Success(other) => assertTrue(s"unexpected: $other" == "")
              case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
      },
      test("parses TIP: paragraph") {
          BlockParser.admonitionParagraphBlock.parse("TIP: Try this.\n") match
              case Success(CstAdmonitionParagraph("TIP", _)) => assertTrue(true)
              case other => assertTrue(s"unexpected: $other" == "")
      },
      test("parses IMPORTANT: paragraph") {
          BlockParser.admonitionParagraphBlock.parse("IMPORTANT: Read this.\n") match
              case Success(CstAdmonitionParagraph("IMPORTANT", _)) => assertTrue(true)
              case other => assertTrue(s"unexpected: $other" == "")
      },
      test("parses CAUTION: paragraph") {
          BlockParser.admonitionParagraphBlock.parse("CAUTION: Be careful.\n") match
              case Success(CstAdmonitionParagraph("CAUTION", _)) => assertTrue(true)
              case other => assertTrue(s"unexpected: $other" == "")
      },
      test("parses WARNING: paragraph") {
          BlockParser.admonitionParagraphBlock.parse("WARNING: Danger.\n") match
              case Success(CstAdmonitionParagraph("WARNING", _)) => assertTrue(true)
              case other => assertTrue(s"unexpected: $other" == "")
      },
      test("NOTE without colon+space is NOT an admonition") {
          BlockParser.admonitionParagraphBlock.parse("NOTE something\n") match
              case Failure(_) => assertTrue(true)
              case Success(r) => assertTrue(s"should have failed, got: $r" == "")
      }
  )
  ```

- [ ] **Step 3: Run tests to confirm they fail**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.parser.BlockParserSpec'
  ```

- [ ] **Step 4: Add `admonitionParagraphBlock` parser to `BlockParser.scala`**

  ```scala
  private val admonitionLabel: Parsley[String] =
      atomic(string("NOTE")) | atomic(string("TIP")) | atomic(string("IMPORTANT")) |
          atomic(string("CAUTION")) | atomic(string("WARNING"))

  val admonitionParagraphBlock: Parsley[CstBlock] =
      atomic(
          (pos <~> (admonitionLabel <* string(": ") <~> lineContent) <~> pos <* eolOrEof)
              .map { case ((s, (kind, content)), e) =>
                  CstAdmonitionParagraph(kind, content)(mkSpan(s, e))
              }
      ).label("admonition paragraph")
  ```

  Update `notCstBlockStart` to reject admonition starters:
  ```scala
  notFollowedBy(
      atomic(admonitionLabel) *> string(": ")
  ) *>
  ```
  Add this near the bottom of the `notCstBlockStart` chain.

  Update `block` to include `admonitionParagraphBlock` before `paragraph`:
  ```scala
  private[parser] val block: Parsley[CstBlock] =
      listingBlock | literalBlock | commentBlock | passBlock |
          sidebarBlock | exampleBlock | quoteBlock | openBlock |
          tableBlock | heading | unorderedList | orderedList |
          lineCommentBlock | includeDirective | attributeEntryBlock |
          admonitionParagraphBlock | paragraph
  ```

  Make `admonitionParagraphBlock` `val` (not `private val`) so `BlockParserSpec` can access it directly. Add `CstAdmonitionParagraph` to imports.

- [ ] **Step 5: Add `CstAdmonitionParagraph` to `CstVisitor` immediately (avoids MatchError in traversal)**

  In `CstVisitor` trait:
  ```scala
  def visitAdmonitionParagraph(node: CstAdmonitionParagraph): A = visitBlock(node)
  ```

  In `CstVisitor.visit`:
  ```scala
  case n: CstAdmonitionParagraph => visitor.visitAdmonitionParagraph(n)
  ```

  In `CstVisitor.children`:
  ```scala
  case ap: CstAdmonitionParagraph => ap.content
  ```

  (The visitor test for this is in Task 11; adding the dispatch now prevents `MatchError`s if any visitor-based code runs before Task 11.)

- [ ] **Step 6: Run tests to confirm they pass**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.parser.BlockParserSpec'
  ```

- [ ] **Step 7: Run full suite**

  ```bash
  ./mill ascribe.test
  ```

- [ ] **Step 8: Commit**

  ```bash
  git add ascribe/src/io/eleven19/ascribe/cst/CstNodes.scala \
          ascribe/src/io/eleven19/ascribe/cst/CstVisitor.scala \
          ascribe/src/io/eleven19/ascribe/parser/BlockParser.scala \
          ascribe/test/src/io/eleven19/ascribe/parser/BlockParserSpec.scala
  git commit -m "feat(parser): add CstAdmonitionParagraph, parser, and CstVisitor dispatch"
  ```

---

## Task 9: AST — `AdmonitionKind` + `Admonition` block

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/ast/Document.scala`
- Modify: `ascribe/src/io/eleven19/ascribe/ast/AstVisitor.scala`
- Modify: `ascribe/test/src/io/eleven19/ascribe/ast/AstVisitorSpec.scala`

- [ ] **Step 1: Write failing visitor test**

  Add to `AstVisitorSpec`:

  ```scala
  test("visitAdmonition dispatches correctly") {
      val admonition = Admonition(AdmonitionKind.Note, List(Paragraph(Nil)(u)))(u)
      val visitor = new AstVisitor[String]:
          def visitNode(node: AstNode): String = "node"
          override def visitAdmonition(node: Admonition): String = s"admonition:${node.kind}"
      assertTrue(admonition.visit(visitor) == "admonition:Note")
  },
  test("admonition children are traversable") {
      val para       = Paragraph(List(Text("warn")(u)))(u)
      val admonition = Admonition(AdmonitionKind.Warning, List(para))(u)
      val texts = admonition.collect { case Text(c) => c }
      assertTrue(texts.contains("warn"))
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail (Admonition not defined yet)**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.ast.AstVisitorSpec'
  ```

- [ ] **Step 3: Add `AdmonitionKind` and `Admonition` to `Document.scala`**

  Add the enum before (or after) the `Block` sealed trait:
  ```scala
  enum AdmonitionKind derives CanEqual:
      case Note, Tip, Important, Caution, Warning
  ```

  Add `Admonition` as a `Block`:
  ```scala
  /** A paragraph-form admonition: `NOTE: text`. The `blocks` list contains a single `Paragraph`.
    * For delimited admonitions (`[NOTE]\n====`), see `Example` with positional attribute.
    */
  case class Admonition(kind: AdmonitionKind, blocks: List[Block])(val span: Span)
      extends Block derives CanEqual
  ```

- [ ] **Step 4: Add `visitAdmonition` to `AstVisitor.scala`**

  In the trait, add:
  ```scala
  def visitAdmonition(node: Admonition): A = visitBlock(node)
  ```

  In `AstVisitor.visit` (the dispatch match), add:
  ```scala
  case n: Admonition => visitor.visitAdmonition(n)
  ```

  In `AstVisitor.children`, add:
  ```scala
  case a: Admonition => a.blocks
  ```

- [ ] **Step 5: Run tests to confirm they pass**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.ast.AstVisitorSpec'
  ```

- [ ] **Step 6: Run full ascribe test suite**

  ```bash
  ./mill ascribe.test
  ```

- [ ] **Step 7: Commit**

  ```bash
  git add ascribe/src/io/eleven19/ascribe/ast/Document.scala \
          ascribe/src/io/eleven19/ascribe/ast/AstVisitor.scala \
          ascribe/test/src/io/eleven19/ascribe/ast/AstVisitorSpec.scala
  git commit -m "feat(ast): add AdmonitionKind enum and Admonition block node"
  ```

---

## Task 10: Lowering — admonition paragraph

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstLowering.scala`
- Modify: `ascribe/test/src/io/eleven19/ascribe/cst/CstLoweringSpec.scala`

- [ ] **Step 1: Write failing lowering tests**

  Add to `CstLoweringSpec`:

  ```scala
  suite("admonition paragraphs")(
      test("NOTE: text lowers to Admonition(Note, [Paragraph])") {
          val cst = CstDocument(None, List(
              CstAdmonitionParagraph("NOTE", List(CstText("Watch out.")(u)))(u)
          ))(u)
          val doc = CstLowering.toAst(cst)
          doc.blocks match
              case List(Admonition(AdmonitionKind.Note, List(Paragraph(_)))) => assertTrue(true)
              case other => assertTrue(s"unexpected: $other" == "")
      },
      test("WARNING: lowers to AdmonitionKind.Warning") {
          val cst = CstDocument(None, List(
              CstAdmonitionParagraph("WARNING", List(CstText("Danger.")(u)))(u)
          ))(u)
          val doc = CstLowering.toAst(cst)
          doc.blocks match
              case List(Admonition(AdmonitionKind.Warning, _)) => assertTrue(true)
              case other => assertTrue(s"unexpected: $other" == "")
      },
      test("all five admonition kinds lower correctly") {
          val kinds = List(
              "NOTE"      -> AdmonitionKind.Note,
              "TIP"       -> AdmonitionKind.Tip,
              "IMPORTANT" -> AdmonitionKind.Important,
              "CAUTION"   -> AdmonitionKind.Caution,
              "WARNING"   -> AdmonitionKind.Warning
          )
          val results = kinds.map { case (label, expected) =>
              val cst = CstDocument(None, List(
                  CstAdmonitionParagraph(label, List(CstText("text")(u)))(u)
              ))(u)
              CstLowering.toAst(cst).blocks match
                  case List(Admonition(kind, _)) => kind == expected
                  case _                         => false
          }
          assertTrue(results.forall(identity))
      }
  )
  ```

  Add imports: `import io.eleven19.ascribe.ast.{AdmonitionKind, Admonition, ...}`

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.cst.CstLoweringSpec'
  ```

- [ ] **Step 3: Add admonition lowering to `lowerBlock` in `CstLowering.scala`**

  Inside the local `def lowerBlock` (added in Task 5), add a case for `CstAdmonitionParagraph`:

  ```scala
  case CstAdmonitionParagraph(kind, content) =>
      val k = kind match
          case "NOTE"      => AdmonitionKind.Note
          case "TIP"       => AdmonitionKind.Tip
          case "IMPORTANT" => AdmonitionKind.Important
          case "CAUTION"   => AdmonitionKind.Caution
          case "WARNING"   => AdmonitionKind.Warning
          case other       => sys.error(s"Unknown admonition kind: $other")
      Some(Admonition(k, List(Paragraph(lowerInlines(content))(block.span)))(block.span))
  ```

  Add imports for `AdmonitionKind` and `Admonition` at the top of `CstLowering.scala`.

- [ ] **Step 4: Run tests to confirm they pass**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.cst.CstLoweringSpec'
  ```

- [ ] **Step 5: Commit**

  ```bash
  git add ascribe/src/io/eleven19/ascribe/cst/CstLowering.scala \
          ascribe/test/src/io/eleven19/ascribe/cst/CstLoweringSpec.scala
  git commit -m "feat(lowering): lower CstAdmonitionParagraph to ast.Admonition"
  ```

---

## Task 11: CST Visitor + Renderer — admonition paragraph

**Files:**
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstVisitor.scala`
- Modify: `ascribe/src/io/eleven19/ascribe/cst/CstRenderer.scala`
- Modify: `ascribe/test/src/io/eleven19/ascribe/cst/CstVisitorSpec.scala`
- Modify: `ascribe/test/src/io/eleven19/ascribe/cst/CstRendererSpec.scala`

- [ ] **Step 1: Write failing tests**

  Add to `CstVisitorSpec`:
  ```scala
  test("collect finds CstAdmonitionParagraph nodes") {
      Ascribe.parseCst("NOTE: Watch out.\n") match
          case Success(doc) =>
              val kinds = doc.collect { case a: CstAdmonitionParagraph => a.kind }
              assertTrue(kinds == List("NOTE"))
          case _ => assertTrue(false)
  }
  ```

  Add to `CstRendererSpec`:
  ```scala
  test("admonition paragraph roundtrips") {
      roundtrip("NOTE: Watch out.\n")
  },
  test("render produces NOTE: prefix") {
      Ascribe.parseCst("NOTE: Watch out.\n") match
          case Success(cst) =>
              val rendered = CstRenderer.render(cst)
              assertTrue(rendered.startsWith("NOTE: "))
          case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
  }
  ```

  Note: `CstVisitor` dispatch/children for `CstAdmonitionParagraph` were already added in Task 8 Step 5. These tests verify that the visitor integration works end-to-end (parse → collect).

- [ ] **Step 2: Run tests to confirm they fail (renderer not yet updated)**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.cst.CstVisitorSpec'
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.cst.CstRendererSpec'
  ```
  Visitor test should pass (dispatch already in place). Renderer tests should fail.

- [ ] **Step 3: Add `CstAdmonitionParagraph` render case to `CstRenderer`**

  In `renderBlock`:
  ```scala
  case ap: CstAdmonitionParagraph =>
      sb.append(ap.kind).append(": ")
      renderInlines(ap.content, sb)
      sb.append('\n')
  ```

- [ ] **Step 4: Run tests to confirm they pass**

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.cst.CstVisitorSpec'
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.cst.CstRendererSpec'
  ```

- [ ] **Step 5: Run full suite**

  ```bash
  ./mill ascribe.test
  ```

- [ ] **Step 6: Commit**

  ```bash
  git add ascribe/src/io/eleven19/ascribe/cst/CstRenderer.scala \
          ascribe/test/src/io/eleven19/ascribe/cst/CstVisitorSpec.scala \
          ascribe/test/src/io/eleven19/ascribe/cst/CstRendererSpec.scala
  git commit -m "feat(cst): render CstAdmonitionParagraph in CstRenderer; add visitor tests"
  ```

---

## Task 12: Bridge — `ast.Admonition` → `asg.Admonition`

**Files:**
- Modify: `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala`
- Modify: `ascribe/bridge/test/src/io/eleven19/ascribe/bridge/AstToAsgSpec.scala`

- [ ] **Step 1: Write failing bridge test**

  Read `ascribe/bridge/test/src/io/eleven19/ascribe/bridge/AstToAsgSpec.scala` to understand the test pattern, then add:

  ```scala
  test("ast.Admonition converts to asg.Admonition with form=paragraph") {
      val para = ast.Paragraph(List(ast.Text("Watch out.")(u)))(u)
      val adm  = ast.Admonition(ast.AdmonitionKind.Note, List(para))(u)
      val doc  = ast.Document(None, List(adm))(u)
      val asgDoc = AstToAsg.convert(doc)
      asgDoc.blocks.toList match
          case List(asg.Admonition(_, _, _, _, "paragraph", "", "note", _, _)) => assertTrue(true)
          case other => assertTrue(s"unexpected: $other" == "")
  }
  ```

  (Match the `asg.Admonition` constructor field order: `id, title, reftext, metadata, form, delimiter, variant, blocks, location, nodeType`. Use wildcards for fields you don't need to check.)

- [ ] **Step 2: Run test to confirm it fails**

  ```bash
  ./mill ascribe.bridge.test.testOnly 'io.eleven19.ascribe.bridge.AstToAsgSpec'
  ```

- [ ] **Step 3: Add `ast.Admonition` case to `AstToAsg.convertBlock`**

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

  Add import: `import io.eleven19.ascribe.ast.{AdmonitionKind, Admonition as AstAdmonition}`

- [ ] **Step 4: Run test to confirm it passes**

  ```bash
  ./mill ascribe.bridge.test.testOnly 'io.eleven19.ascribe.bridge.AstToAsgSpec'
  ```

- [ ] **Step 5: Run full bridge test suite**

  ```bash
  ./mill ascribe.bridge.test
  ```

- [ ] **Step 6: Commit**

  ```bash
  git add ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala \
          ascribe/bridge/test/src/io/eleven19/ascribe/bridge/AstToAsgSpec.scala
  git commit -m "feat(bridge): convert ast.Admonition (paragraph form) to asg.Admonition"
  ```

---

## Task 13: Pipeline renderer — `ast.Admonition`

**Files:**
- Modify: `ascribe/pipeline/src/io/eleven19/ascribe/pipeline/AsciiDocRenderer.scala`
- Modify: `ascribe/pipeline/test/src/io/eleven19/ascribe/pipeline/AsciiDocRendererSpec.scala`

- [ ] **Step 1: Write failing test**

  Add to `AsciiDocRendererSpec`:

  ```scala
  test("renders NOTE admonition paragraph") {
      val para = Paragraph(List(Text("Watch out.")(Span.unknown)))(Span.unknown)
      val adm  = Admonition(AdmonitionKind.Note, List(para))(Span.unknown)
      val doc  = Document(None, List(adm))(Span.unknown)
      val rendered = AsciiDocRenderer.render(doc).result
      assertTrue(rendered.contains("NOTE: Watch out."))
  },
  test("renders WARNING admonition paragraph") {
      val para = Paragraph(List(Text("Danger!")(Span.unknown)))(Span.unknown)
      val adm  = Admonition(AdmonitionKind.Warning, List(para))(Span.unknown)
      val doc  = Document(None, List(adm))(Span.unknown)
      val rendered = AsciiDocRenderer.render(doc).result
      assertTrue(rendered.contains("WARNING: Danger!"))
  }
  ```

- [ ] **Step 2: Run test to confirm it fails**

  ```bash
  ./mill ascribe.pipeline.test.testOnly 'io.eleven19.ascribe.pipeline.AsciiDocRendererSpec'
  ```

- [ ] **Step 3: Add `Admonition` case to `AsciiDocRenderer.renderBlockTo`**

  ```scala
  case Admonition(kind, blocks) =>
      val label = kind.toString.toUpperCase
      blocks match
          case List(Paragraph(content)) =>
              sb.append(label).append(": ").append(renderInlines(content)).append('\n')
          case _ =>
              renderBlocks(blocks, sb)
  ```

  Add imports: `import io.eleven19.ascribe.ast.{Admonition, AdmonitionKind}`

- [ ] **Step 4: Run test to confirm it passes**

  ```bash
  ./mill ascribe.pipeline.test.testOnly 'io.eleven19.ascribe.pipeline.AsciiDocRendererSpec'
  ```

- [ ] **Step 5: Run all tests across all modules**

  ```bash
  ./mill ascribe.test && ./mill ascribe.bridge.test && ./mill ascribe.pipeline.test
  ```
  Expected: all pass.

- [ ] **Step 6: Commit**

  ```bash
  git add ascribe/pipeline/src/io/eleven19/ascribe/pipeline/AsciiDocRenderer.scala \
          ascribe/pipeline/test/src/io/eleven19/ascribe/pipeline/AsciiDocRendererSpec.scala
  git commit -m "feat(pipeline): render ast.Admonition as NOTE:/TIP:/etc. paragraph"
  ```

---

## Task 14: Integration — end-to-end parse + lower + render

**Files:**
- Modify: `ascribe/test/src/io/eleven19/ascribe/AscribeSpec.scala`

- [ ] **Step 1: Write end-to-end tests using `Ascribe.parse`**

  Read `ascribe/test/src/io/eleven19/ascribe/AscribeSpec.scala` to understand the test pattern, then add:

  ```scala
  suite("attribute references")(
      test("resolves {attr} from header via full pipeline") {
          val src = "= Doc\n:version: 2.0\n\nRelease {version}.\n"
          Ascribe.parse(src) match
              case Success(doc) =>
                  val texts = doc.blocks.flatMap {
                      case Paragraph(content) => content.collect { case Text(c) => c }
                      case _                  => Nil
                  }
                  assertTrue(texts.contains("2.0"))
              case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
      },
      test("resolves {attr} from body entry") {
          val src = ":greeting: Hello\n\n{greeting} world.\n"
          Ascribe.parse(src) match
              case Success(doc) =>
                  val texts = doc.blocks.flatMap {
                      case Paragraph(content) => content.collect { case Text(c) => c }
                      case _                  => Nil
                  }
                  assertTrue(texts.contains("Hello"))
              case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
      }
  ),
  suite("admonition paragraphs")(
      test("NOTE: text parses to ast.Admonition") {
          Ascribe.parse("NOTE: Watch out.\n") match
              case Success(doc) =>
                  doc.blocks match
                      case List(Admonition(AdmonitionKind.Note, _)) => assertTrue(true)
                      case other => assertTrue(s"unexpected: $other" == "")
              case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
      },
      test("delimited [NOTE]==== still flows to ast.Example with attr") {
          Ascribe.parse("[NOTE]\n====\nWatch out.\n====\n") match
              case Success(doc) =>
                  doc.blocks match
                      case List(Example(_, _, Some(attrs), _))
                          if attrs.positional.exists(_.value == "NOTE") =>
                          assertTrue(true)
                      case other => assertTrue(s"unexpected blocks: $other" == "")
              case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
      }
  )
  ```

- [ ] **Step 2: Run tests to confirm they pass** (should pass since all pipeline steps are done)

  ```bash
  ./mill ascribe.test.testOnly 'io.eleven19.ascribe.AscribeSpec'
  ```

- [ ] **Step 3: Update beads issues**

  ```bash
  bd close ascribe-a4e --reason="Attribute references and substitution implemented: CstAttributeRef, AttributeMap, body-level entries, built-ins, unset form"
  bd close ascribe-ch0 --reason="Admonition paragraph form implemented: CstAdmonitionParagraph, ast.Admonition, bridge, renderer"
  ```

- [ ] **Step 4: Final commit**

  ```bash
  git add ascribe/test/src/io/eleven19/ascribe/AscribeSpec.scala
  git commit -m "test: add end-to-end integration tests for attr refs and admonitions"
  ```

- [ ] **Step 5: Push**

  ```bash
  git push
  ```
