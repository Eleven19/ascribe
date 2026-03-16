# ASG Module + JSON Serialization Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the `ascribe-asg` module (ASG types + zio-blocks JSON codecs) and `ascribe-bridge` module (AST → ASG converter), enabling TCK JSON comparison.

**Architecture:** Two new Mill modules. `ascribe/asg` contains the full ASG type hierarchy per the official AsciiDoc ASG JSON schema with zio-blocks-schema codecs. `ascribe/bridge` depends on both `ascribe` and `ascribe/asg`, providing a converter from parsed AST to ASG. Types use sealed abstract classes with computed `name`/`nodeType` discriminators.

**Tech Stack:** Scala 3.8.2, Mill 1.1.3, zio-blocks-schema 0.0.29 (includes JSON codecs, JsonDiffer, JsonPatch), zio.Chunk

**Spec:** `docs/superpowers/specs/2026-03-16-asg-module-design.md`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `ascribe/asg/package.mill.yaml` | Create | Module config with zio-blocks-schema dep |
| `ascribe/asg/src/io/eleven19/ascribe/asg/Position.scala` | Create | `Position`, `Location` type alias |
| `ascribe/asg/src/io/eleven19/ascribe/asg/Node.scala` | Create | `Node`, `Block`, `Inline` sealed abstract classes |
| `ascribe/asg/src/io/eleven19/ascribe/asg/BlockMetadata.scala` | Create | `BlockMetadata` case class |
| `ascribe/asg/src/io/eleven19/ascribe/asg/Header.scala` | Create | `Header`, `Author` case classes |
| `ascribe/asg/src/io/eleven19/ascribe/asg/blocks.scala` | Create | All `Block` subtypes (Document, Section, Paragraph, etc.) |
| `ascribe/asg/src/io/eleven19/ascribe/asg/inlines.scala` | Create | All `Inline` subtypes (Text, Span, Ref, etc.) |
| `ascribe/asg/test/package.mill.yaml` | Create | Test module config |
| `ascribe/asg/test/src/io/eleven19/ascribe/asg/CodecSpec.scala` | Create | JSON roundtrip tests |
| `ascribe/bridge/package.mill.yaml` | Create | Module config depending on ascribe + asg |
| `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala` | Create | AST → ASG converter |
| `ascribe/bridge/test/package.mill.yaml` | Create | Test module config |
| `ascribe/bridge/test/src/io/eleven19/ascribe/bridge/AstToAsgSpec.scala` | Create | Converter tests |

---

## Chunk 1: ASG Module Setup and Base Types

### Task 1: Create ASG module with build config

**Files:**
- Create: `ascribe/asg/package.mill.yaml`
- Create: `ascribe/asg/test/package.mill.yaml`

- [ ] **Step 1: Create the ASG module build config**

```yaml
# ascribe/asg/package.mill.yaml
extends: [ScalaModule, scalafmt.ScalafmtModule]

scalaVersion: "3.8.2"

mvnDeps:
  - dev.zio::zio-blocks-schema::0.0.29
```

- [ ] **Step 2: Create the ASG test module build config**

```yaml
# ascribe/asg/test/package.mill.yaml
extends: [ascribe.asg.ScalaTests, TestModule.Junit5]

mvnDeps:
  - org.scalameta::munit::1.0.4
```

- [ ] **Step 3: Verify modules resolve**

Run: `./mill resolve ascribe.asg._`
Expected: Shows ascribe.asg tasks

- [ ] **Step 4: Commit**

```bash
git add ascribe/asg/package.mill.yaml ascribe/asg/test/package.mill.yaml
git commit -m "feat: add ascribe-asg module with zio-blocks-schema dependency"
```

### Task 2: Create Position and Location types

**Files:**
- Create: `ascribe/asg/src/io/eleven19/ascribe/asg/Position.scala`

- [ ] **Step 1: Create Position.scala**

```scala
package io.eleven19.ascribe.asg

import zio.Chunk

/** A source position identified by line and column (both 1-based). */
case class Position(
    line: Int,
    col: Int,
    file: Option[Chunk[String]] = None
)

/** A source location as a pair of positions (start, end). */
type Location = Chunk[Position]
```

- [ ] **Step 2: Verify it compiles**

Run: `./mill ascribe.asg.compile`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ascribe/asg/src/io/eleven19/ascribe/asg/Position.scala
git commit -m "feat(asg): add Position and Location types"
```

### Task 3: Create Node, Block, and Inline base classes

**Files:**
- Create: `ascribe/asg/src/io/eleven19/ascribe/asg/Node.scala`

- [ ] **Step 1: Create Node.scala**

```scala
package io.eleven19.ascribe.asg

import zio.Chunk

/** Base class for all ASG nodes. Every node has a name (type discriminator)
  * and a nodeType (category: "block", "inline", or "string").
  * These are immutable and determined by the concrete type.
  */
sealed abstract class Node(val name: String, val nodeType: String):
  def location: Location

/** Base class for block-level ASG nodes. All blocks have type "block"
  * and share optional id, title, reftext, and metadata fields.
  */
sealed abstract class Block(name: String) extends Node(name, "block"):
  def id: Option[String]
  def title: Option[Chunk[Inline]]
  def reftext: Option[Chunk[Inline]]
  def metadata: Option[BlockMetadata]

/** Base class for inline ASG nodes. Inline nodes have varying nodeType:
  * parent inlines (Span, Ref) use "inline", literal inlines (Text, CharRef, Raw) use "string".
  */
sealed abstract class Inline(name: String, nodeType: String) extends Node(name, nodeType)
```

- [ ] **Step 2: Verify it compiles**

Run: `./mill ascribe.asg.compile`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ascribe/asg/src/io/eleven19/ascribe/asg/Node.scala
git commit -m "feat(asg): add Node, Block, and Inline sealed abstract classes"
```

### Task 4: Create BlockMetadata, Header, and Author

**Files:**
- Create: `ascribe/asg/src/io/eleven19/ascribe/asg/BlockMetadata.scala`
- Create: `ascribe/asg/src/io/eleven19/ascribe/asg/Header.scala`

- [ ] **Step 1: Create BlockMetadata.scala**

```scala
package io.eleven19.ascribe.asg

import zio.Chunk

/** Metadata that can be attached to any block node. */
case class BlockMetadata(
    attributes: Map[String, String] = Map.empty,
    options: Chunk[String] = Chunk.empty,
    roles: Chunk[String] = Chunk.empty,
    location: Option[Location] = None
)
```

- [ ] **Step 2: Create Header.scala**

```scala
package io.eleven19.ascribe.asg

import zio.Chunk

/** Document header with optional title and authors.
  * Not a Node — embedded within Document.
  * All fields are optional per the ASG schema.
  */
case class Header(
    title: Option[Chunk[Inline]] = None,
    authors: Chunk[Author] = Chunk.empty,
    location: Option[Location] = None
)

/** Author information from the document header. */
case class Author(
    fullname: Option[String] = None,
    initials: Option[String] = None,
    firstname: Option[String] = None,
    middlename: Option[String] = None,
    lastname: Option[String] = None,
    address: Option[String] = None
)
```

- [ ] **Step 3: Verify it compiles**

Run: `./mill ascribe.asg.compile`
Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add ascribe/asg/src/io/eleven19/ascribe/asg/BlockMetadata.scala
git add ascribe/asg/src/io/eleven19/ascribe/asg/Header.scala
git commit -m "feat(asg): add BlockMetadata, Header, and Author types"
```

## Chunk 2: Block and Inline Types

### Task 5: Create all Block subtypes

**Files:**
- Create: `ascribe/asg/src/io/eleven19/ascribe/asg/blocks.scala`

- [ ] **Step 1: Create blocks.scala with all block types**

This is a large file containing all concrete Block subtypes. Each follows the pattern: extend `Block(name)` or `Node(name, "block")` for Document, provide `id`, `title`, `reftext`, `metadata` as `Option` with `None` defaults.

```scala
package io.eleven19.ascribe.asg

import zio.Chunk

// --- Document (extends Node directly, not Block — per ASG schema) ---

case class Document(
    attributes: Option[Map[String, Option[String]]] = None,
    header: Option[Header] = None,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Node("document", "block")

// --- Section and discrete Heading ---

case class Section(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    level: Int,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("section")

case class Heading(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    level: Int,
    location: Location
) extends Block("heading")

// --- Leaf blocks ---

case class Paragraph(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: Option[String] = None,
    delimiter: Option[String] = None,
    inlines: Chunk[Inline] = Chunk.empty,
    location: Location
) extends Block("paragraph")

case class Listing(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: Option[String] = None,
    delimiter: Option[String] = None,
    inlines: Chunk[Inline] = Chunk.empty,
    location: Location
) extends Block("listing")

case class Literal(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: Option[String] = None,
    delimiter: Option[String] = None,
    inlines: Chunk[Inline] = Chunk.empty,
    location: Location
) extends Block("literal")

case class Pass(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: Option[String] = None,
    delimiter: Option[String] = None,
    inlines: Chunk[Inline] = Chunk.empty,
    location: Location
) extends Block("pass")

case class Stem(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: Option[String] = None,
    delimiter: Option[String] = None,
    inlines: Chunk[Inline] = Chunk.empty,
    location: Location
) extends Block("stem")

case class Verse(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: Option[String] = None,
    delimiter: Option[String] = None,
    inlines: Chunk[Inline] = Chunk.empty,
    location: Location
) extends Block("verse")

// --- Parent blocks ---

case class Sidebar(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String,
    delimiter: String,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("sidebar")

case class Example(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String,
    delimiter: String,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("example")

case class Admonition(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String,
    delimiter: String,
    variant: String,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("admonition")

case class Open(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String,
    delimiter: String,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("open")

case class Quote(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String,
    delimiter: String,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("quote")

// --- Lists ---

case class List(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    variant: String,
    marker: String,
    items: Chunk[ListItem],
    location: Location
) extends Block("list")

case class DList(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    marker: String,
    items: Chunk[DListItem],
    location: Location
) extends Block("dlist")

case class ListItem(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    marker: String,
    principal: Chunk[Inline],
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("listItem")

case class DListItem(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    marker: String,
    terms: Chunk[Chunk[Inline]],
    principal: Option[Chunk[Inline]] = None,
    blocks: Chunk[Block] = Chunk.empty,
    location: Location
) extends Block("dlistItem")

// --- Break ---

case class Break(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    variant: String,
    location: Location
) extends Block("break")

// --- Block macros (each a concrete type with fixed name) ---

case class Audio(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String = "macro",
    target: Option[String] = None,
    location: Location
) extends Block("audio")

case class Video(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String = "macro",
    target: Option[String] = None,
    location: Location
) extends Block("video")

case class Image(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String = "macro",
    target: Option[String] = None,
    location: Location
) extends Block("image")

case class Toc(
    id: Option[String] = None,
    title: Option[Chunk[Inline]] = None,
    reftext: Option[Chunk[Inline]] = None,
    metadata: Option[BlockMetadata] = None,
    form: String = "macro",
    target: Option[String] = None,
    location: Location
) extends Block("toc")
```

- [ ] **Step 2: Verify it compiles**

Run: `./mill ascribe.asg.compile`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ascribe/asg/src/io/eleven19/ascribe/asg/blocks.scala
git commit -m "feat(asg): add all Block subtypes per ASG schema"
```

### Task 6: Create all Inline subtypes

**Files:**
- Create: `ascribe/asg/src/io/eleven19/ascribe/asg/inlines.scala`

- [ ] **Step 1: Create inlines.scala**

```scala
package io.eleven19.ascribe.asg

import zio.Chunk

// --- Parent inlines (contain child inlines, nodeType = "inline") ---

/** Inline formatting span (strong, emphasis, code, mark). */
case class Span(
    variant: String,
    form: String,
    inlines: Chunk[Inline],
    location: Location
) extends Inline("span", "inline")

/** Inline reference (link, xref). */
case class Ref(
    variant: String,
    target: String,
    inlines: Chunk[Inline],
    location: Location
) extends Inline("ref", "inline")

// --- Literal inlines (leaf nodes with string values, nodeType = "string") ---

/** Plain text content. */
case class Text(
    value: String,
    location: Location
) extends Inline("text", "string")

/** Character reference. */
case class CharRef(
    value: String,
    location: Location
) extends Inline("charref", "string")

/** Raw (passthrough) inline content. */
case class Raw(
    value: String,
    location: Location
) extends Inline("raw", "string")
```

- [ ] **Step 2: Verify it compiles**

Run: `./mill ascribe.asg.compile`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ascribe/asg/src/io/eleven19/ascribe/asg/inlines.scala
git commit -m "feat(asg): add all Inline subtypes per ASG schema"
```

## Chunk 3: JSON Codecs and Tests

### Task 7: Add JSON codec tests (TDD — tests first)

**Files:**
- Create: `ascribe/asg/test/src/io/eleven19/ascribe/asg/CodecSpec.scala`

- [ ] **Step 1: Write codec roundtrip tests**

These tests verify that ASG types can be encoded to JSON and decoded back. Start with the simplest types and work up. The tests will fail until codecs are implemented.

```scala
package io.eleven19.ascribe.asg

import zio.Chunk
import munit.FunSuite

class CodecSpec extends FunSuite:

  val loc: Location = Chunk(Position(1, 1), Position(1, 10))

  test("Text roundtrip") {
    val text = Text("hello", loc)
    val json = AsgCodecs.encode(text: Node)
    val decoded = AsgCodecs.decode(json)
    assertEquals(decoded, Right(text))
  }

  test("Paragraph with text roundtrip") {
    val para = Paragraph(
      inlines = Chunk(Text("hello", loc)),
      location = loc
    )
    val json = AsgCodecs.encode(para: Node)
    val decoded = AsgCodecs.decode(json)
    assertEquals(decoded, Right(para))
  }

  test("Document with paragraph roundtrip") {
    val doc = Document(
      blocks = Chunk(
        Paragraph(inlines = Chunk(Text("hello", loc)), location = loc)
      ),
      location = loc
    )
    val json = AsgCodecs.encode(doc: Node)
    val decoded = AsgCodecs.decode(json)
    assertEquals(decoded, Right(doc))
  }

  test("Text JSON contains name and type fields") {
    val text = Text("hello", loc)
    val json = AsgCodecs.encode(text: Node)
    assert(json.contains("\"name\""))
    assert(json.contains("\"text\""))
    assert(json.contains("\"type\""))
    assert(json.contains("\"string\""))
    assert(json.contains("\"value\""))
    assert(json.contains("\"hello\""))
  }

  test("Span with strong variant roundtrip") {
    val span = Span(
      variant = "strong",
      form = "unconstrained",
      inlines = Chunk(Text("bold", loc)),
      location = loc
    )
    val json = AsgCodecs.encode(span: Node)
    val decoded = AsgCodecs.decode(json)
    assertEquals(decoded, Right(span))
  }

  test("List with items roundtrip") {
    val list = List(
      variant = "unordered",
      marker = "*",
      items = Chunk(
        ListItem(marker = "*", principal = Chunk(Text("item", loc)), location = loc)
      ),
      location = loc
    )
    val json = AsgCodecs.encode(list: Node)
    val decoded = AsgCodecs.decode(json)
    assertEquals(decoded, Right(list))
  }
```

- [ ] **Step 2: Verify tests fail (AsgCodecs not yet implemented)**

Run: `./mill ascribe.asg.test.compile`
Expected: FAIL — `AsgCodecs` not found

- [ ] **Step 3: Commit test file**

```bash
git add ascribe/asg/test/src/io/eleven19/ascribe/asg/CodecSpec.scala
git commit -m "test(asg): add JSON codec roundtrip tests (red)"
```

### Task 8: Implement JSON codecs

**Files:**
- Create: `ascribe/asg/src/io/eleven19/ascribe/asg/AsgCodecs.scala`

- [ ] **Step 1: Create AsgCodecs.scala**

This is the most complex part. The `name` and `type` fields in the JSON come from the sealed hierarchy's abstract class vals, not from case class fields. We need custom codec handling.

The implementation approach depends on how zio-blocks-schema handles sealed hierarchies with constructor-val discriminators. The implementer should:

1. First try `Schema.derived` on the `Node` sealed hierarchy to see if it auto-detects `name` as a discriminator
2. If auto-derivation doesn't produce the correct JSON shape (with `name` and `type` fields), use `JsonBinaryCodecDeriver` with custom configuration
3. If custom configuration is insufficient, implement manual `JsonEncoder`/`JsonDecoder` instances

Provide a facade object regardless of the underlying approach:

```scala
package io.eleven19.ascribe.asg

/** JSON codec facade for ASG nodes.
  * Handles encoding ASG nodes to JSON strings and decoding JSON strings back to ASG nodes.
  * The JSON format matches the AsciiDoc TCK's expected ASG JSON schema.
  */
object AsgCodecs:
  /** Encode an ASG Node to a JSON string. */
  def encode(node: Node): String = ???

  /** Decode a JSON string to an ASG Node. */
  def decode(json: String): Either[String, Node] = ???
```

The implementer must explore zio-blocks-schema's API to determine the best approach. Key things to verify:
- Does `Schema.derived[Node]` work with sealed abstract classes?
- How are `val name` and `val nodeType` handled — included in JSON or not?
- Does `Document` extending `Node` directly (not `Block`) work with the same discriminator?
- How to configure the JSON field name for `nodeType` → `"type"` in JSON output

If `Schema.derived` does not work out of the box, the implementer should consult the zio-blocks documentation and source code. As a last resort, manual `JsonEncoder`/`JsonDecoder` instances can be written for each type.

- [ ] **Step 2: Verify tests pass**

Run: `./mill ascribe.asg.test`
Expected: All codec tests pass

- [ ] **Step 3: Commit**

```bash
git add ascribe/asg/src/io/eleven19/ascribe/asg/AsgCodecs.scala
git commit -m "feat(asg): implement JSON codecs for ASG types"
```

## Chunk 4: Bridge Module

### Task 9: Create bridge module with build config

**Files:**
- Create: `ascribe/bridge/package.mill.yaml`
- Create: `ascribe/bridge/test/package.mill.yaml`

- [ ] **Step 1: Create bridge module build config**

```yaml
# ascribe/bridge/package.mill.yaml
extends: [ScalaModule, scalafmt.ScalafmtModule]

scalaVersion: "3.8.2"

moduleDeps:
  - ascribe
  - ascribe.asg
```

- [ ] **Step 2: Create bridge test module config**

```yaml
# ascribe/bridge/test/package.mill.yaml
extends: [ascribe.bridge.ScalaTests, TestModule.Junit5]

mvnDeps:
  - org.scalameta::munit::1.0.4
```

- [ ] **Step 3: Verify modules resolve**

Run: `./mill resolve ascribe.bridge._`
Expected: Shows ascribe.bridge tasks

- [ ] **Step 4: Commit**

```bash
git add ascribe/bridge/package.mill.yaml ascribe/bridge/test/package.mill.yaml
git commit -m "feat: add ascribe-bridge module"
```

### Task 10: Add converter tests (TDD — tests first)

**Files:**
- Create: `ascribe/bridge/test/src/io/eleven19/ascribe/bridge/AstToAsgSpec.scala`

- [ ] **Step 1: Write converter tests**

Tests use `TestHelpers` from the AST test module for convenience. Import both `ast` and `asg` packages with qualified names.

```scala
package io.eleven19.ascribe.bridge

import zio.Chunk
import munit.FunSuite
import io.github.eleven19.ascribe.{ast, TestHelpers as H}
import io.eleven19.ascribe.{asg}

class AstToAsgSpec extends FunSuite:

  test("converts empty document") {
    val astDoc = H.document()
    val asgDoc = AstToAsg.convert(astDoc)
    assertEquals(asgDoc.name, "document")
    assertEquals(asgDoc.blocks, Chunk.empty)
  }

  test("converts paragraph with plain text") {
    val astDoc = H.document(H.paragraph(H.text("hello")))
    val asgDoc = AstToAsg.convert(astDoc)
    assertEquals(asgDoc.blocks.size, 1)
    asgDoc.blocks.head match
      case p: asg.Paragraph =>
        assertEquals(p.inlines.size, 1)
        p.inlines.head match
          case t: asg.Text => assertEquals(t.value, "hello")
          case other => fail(s"Expected Text, got $other")
      case other => fail(s"Expected Paragraph, got $other")
  }

  test("converts heading to section") {
    val astDoc = H.document(H.heading(1, H.text("Title")))
    val asgDoc = AstToAsg.convert(astDoc)
    asgDoc.blocks.head match
      case s: asg.Section =>
        assertEquals(s.level, 1)
      case other => fail(s"Expected Section, got $other")
  }

  test("converts bold to strong span") {
    val astDoc = H.document(H.paragraph(H.bold(H.text("bold"))))
    val asgDoc = AstToAsg.convert(astDoc)
    val para = asgDoc.blocks.head.asInstanceOf[asg.Paragraph]
    para.inlines.head match
      case s: asg.Span =>
        assertEquals(s.variant, "strong")
        assertEquals(s.form, "unconstrained")
      case other => fail(s"Expected Span, got $other")
  }

  test("converts italic to emphasis span") {
    val astDoc = H.document(H.paragraph(H.italic(H.text("em"))))
    val asgDoc = AstToAsg.convert(astDoc)
    val para = asgDoc.blocks.head.asInstanceOf[asg.Paragraph]
    para.inlines.head match
      case s: asg.Span =>
        assertEquals(s.variant, "emphasis")
        assertEquals(s.form, "unconstrained")
      case other => fail(s"Expected Span, got $other")
  }

  test("converts mono to code span") {
    val astDoc = H.document(H.paragraph(H.mono(H.text("code"))))
    val asgDoc = AstToAsg.convert(astDoc)
    val para = asgDoc.blocks.head.asInstanceOf[asg.Paragraph]
    para.inlines.head match
      case s: asg.Span =>
        assertEquals(s.variant, "code")
        assertEquals(s.form, "unconstrained")
      case other => fail(s"Expected Span, got $other")
  }

  test("converts unordered list") {
    val astDoc = H.document(H.unorderedList(H.listItem(H.text("item"))))
    val asgDoc = AstToAsg.convert(astDoc)
    asgDoc.blocks.head match
      case l: asg.List =>
        assertEquals(l.variant, "unordered")
        assertEquals(l.marker, "*")
        assertEquals(l.items.size, 1)
      case other => fail(s"Expected List, got $other")
  }

  test("converts ordered list") {
    val astDoc = H.document(H.orderedList(H.listItem(H.text("item"))))
    val asgDoc = AstToAsg.convert(astDoc)
    asgDoc.blocks.head match
      case l: asg.List =>
        assertEquals(l.variant, "ordered")
        assertEquals(l.marker, ".")
      case other => fail(s"Expected List, got $other")
  }

  test("converts position spans") {
    val span = ast.Span(ast.Position(1, 1), ast.Position(1, 10))
    val astDoc = ast.Document(
      scala.List(ast.Paragraph(scala.List(ast.Text("hi")(span)))(span))
    )(span)
    val asgDoc = AstToAsg.convert(astDoc)
    assertEquals(asgDoc.location, Chunk(asg.Position(1, 1), asg.Position(1, 10)))
  }
```

Note: `TestHelpers` is in the `ascribe.test` source set, which is not a dependency of `ascribe.bridge.test`. The implementer should either:
1. Make `TestHelpers` available as a test utility (move to a shared test-utils module), or
2. Construct AST nodes directly using `Span.unknown` in the bridge tests

Option 2 is simpler for now.

- [ ] **Step 2: Verify tests fail**

Run: `./mill ascribe.bridge.test.compile`
Expected: FAIL — `AstToAsg` not found

- [ ] **Step 3: Commit**

```bash
git add ascribe/bridge/test/src/io/eleven19/ascribe/bridge/AstToAsgSpec.scala
git commit -m "test(bridge): add AST to ASG converter tests (red)"
```

### Task 11: Implement AST → ASG converter

**Files:**
- Create: `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala`

- [ ] **Step 1: Create AstToAsg.scala**

```scala
package io.eleven19.ascribe.bridge

import zio.Chunk
import io.github.eleven19.ascribe.{ast}
import io.eleven19.ascribe.{asg}

/** Converts parsed AST documents to ASG format. */
object AstToAsg:

  /** Convert an AST Document to an ASG Document. */
  def convert(doc: ast.Document): asg.Document =
    asg.Document(
      blocks = Chunk.from(doc.blocks.map(convertBlock)),
      location = convertLocation(doc.span)
    )

  private def convertBlock(block: ast.Block): asg.Block = block match
    case ast.Heading(level, title) =>
      asg.Section(
        level = level,
        title = Some(Chunk.from(title.map(convertInline))),
        location = convertLocation(block.span)
      )
    case ast.Paragraph(content) =>
      asg.Paragraph(
        inlines = Chunk.from(content.map(convertInline)),
        location = convertLocation(block.span)
      )
    case ast.UnorderedList(items) =>
      asg.List(
        variant = "unordered",
        marker = "*",
        items = Chunk.from(items.map(convertListItem)),
        location = convertLocation(block.span)
      )
    case ast.OrderedList(items) =>
      asg.List(
        variant = "ordered",
        marker = ".",
        items = Chunk.from(items.map(convertListItem)),
        location = convertLocation(block.span)
      )

  private def convertListItem(item: ast.ListItem): asg.ListItem =
    asg.ListItem(
      marker = "*",
      principal = Chunk.from(item.content.map(convertInline)),
      location = convertLocation(item.span)
    )

  private def convertInline(inline: ast.Inline): asg.Inline = inline match
    case ast.Text(content) =>
      asg.Text(value = content, location = convertLocation(inline.span))
    case ast.Bold(content) =>
      asg.Span(
        variant = "strong",
        form = "unconstrained",
        inlines = Chunk.from(content.map(convertInline)),
        location = convertLocation(inline.span)
      )
    case ast.Italic(content) =>
      asg.Span(
        variant = "emphasis",
        form = "unconstrained",
        inlines = Chunk.from(content.map(convertInline)),
        location = convertLocation(inline.span)
      )
    case ast.Mono(content) =>
      asg.Span(
        variant = "code",
        form = "unconstrained",
        inlines = Chunk.from(content.map(convertInline)),
        location = convertLocation(inline.span)
      )

  private def convertLocation(span: ast.Span): asg.Location =
    Chunk(
      asg.Position(span.start.line, span.start.col),
      asg.Position(span.end.line, span.end.col)
    )
```

- [ ] **Step 2: Verify tests pass**

Run: `./mill ascribe.bridge.test`
Expected: All converter tests pass

- [ ] **Step 3: Commit**

```bash
git add ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala
git commit -m "feat(bridge): implement AST to ASG converter"
```

## Chunk 5: Verification

### Task 12: Full verification

- [ ] **Step 1: Full compilation**

Run: `./mill __.compile`
Expected: All modules compile successfully

- [ ] **Step 2: All tests**

Run: `./mill ascribe.test && ./mill ascribe.itest && ./mill ascribe.asg.test && ./mill ascribe.bridge.test`
Expected: All tests pass

- [ ] **Step 3: Format check**

Run: `./mill __.checkFormat`
Expected: Clean. If violations, run `./mill __.reformat` then commit.

- [ ] **Step 4: Commit any format fixes**

```bash
git add -A
git commit -m "style: apply formatting"
```
