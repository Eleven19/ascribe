# Kyo Schema Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace ASG usage of `zio-blocks-schema` and `zio.blocks.chunk.Chunk` with `kyo-schema` and `kyo.Chunk`, preserving the current ASG/TCK JSON contract.

**Architecture:** Keep the public ASG domain model and `AsgCodecs` API stable. Use Kyo `Structure.Value` as the explicit ASG wire tree, then serialize/deserialize that tree with `kyo.Json`; this uses kyo-schema while keeping discriminator names and field omission rules under Ascribe control. Leave `ascribe/pipeline/markdown` and `zio-blocks-docs` unchanged.

**Tech Stack:** Scala 3.8.4, Mill 1.2.0-RC1, Kyo 1.0.0-RC4, kyo-schema 1.0.0-RC4, Kyo `Chunk`, MUnit, kyo-test.

---

## Reference Context

- Design spec: `docs/superpowers/specs/2026-06-20-kyo-schema-migration-design.md`
- Beads issue: `ascribe-3p8`
- Upstream Kyo issue for missing discriminator variant-name mapping: https://github.com/getkyo/kyo/issues/1691

Kyo `Structure.Value` has an identity `Schema[Structure.Value]`: records write as JSON objects, sequences as arrays, and scalars as normal JSON scalars. It reads JSON through the introspecting JSON reader. This lets `AsgCodecs` construct the exact ASG/TCK wire tree and still use kyo-schema/Json for actual encoding and decoding.

## File Map

- Modify: `ascribe/asg/package.mill`
  - Replace `dev.zio::zio-blocks-schema::0.0.29` with `io.getkyo::kyo-schema::1.0.0-RC4`.
- Modify: `ascribe/asg/src/io/eleven19/ascribe/asg/Node.scala`
  - Replace `zio.blocks.chunk.Chunk` with `kyo.Chunk`.
  - Remove `zio.blocks.schema` imports and `derives Schema`.
  - Remove `@Modifier.rename("type")`; the codec layer owns wire field naming.
- Modify: `ascribe/asg/src/io/eleven19/ascribe/asg/Position.scala`
  - Replace `zio.blocks.chunk.Chunk` with `kyo.Chunk`.
  - Remove zio schema transform.
- Modify: `ascribe/asg/src/io/eleven19/ascribe/asg/Header.scala`
  - Replace `Chunk` import and remove `derives Schema`.
- Modify: `ascribe/asg/src/io/eleven19/ascribe/asg/BlockMetadata.scala`
  - Replace `Chunk` import and remove `derives Schema`.
- Modify: `ascribe/asg/src/io/eleven19/ascribe/asg/ColumnSpec.scala`
  - Replace zio schema givens with plain domain types; schema handling moves into wire codec.
- Modify: `ascribe/asg/src/io/eleven19/ascribe/asg/AsgCodecs.scala`
  - Reimplement with `kyo.Json`, `kyo.Structure`, and explicit ASG wire conversion.
- Create: `ascribe/asg/src/io/eleven19/ascribe/asg/AsgWire.scala`
  - Private helpers for converting ASG nodes to/from `Structure.Value`.
- Modify: ASG and bridge tests/imports under:
  - `ascribe/asg/test/src/io/eleven19/ascribe/asg/*.scala`
  - `ascribe/bridge/src/io/eleven19/ascribe/bridge/*.scala`
  - `ascribe/bridge/test/src/io/eleven19/ascribe/bridge/*.scala`
  - `ascribe/tck-runner/test/src/build/ascribe/tckrunner/TckSteps.scala` only if imports/types need adjustment.
- Do not modify:
  - `ascribe/pipeline/markdown/package.mill`
  - `ascribe/pipeline/markdown/src/io/eleven19/ascribe/pipeline/markdown/GfmMarkdown.scala`

---

### Task 1: Pin Current ASG JSON With Golden Tests

**Files:**
- Modify: `ascribe/asg/test/src/io/eleven19/ascribe/asg/CodecSpec.scala`

- [ ] **Step 1: Add structural JSON helper imports**

Add imports to `CodecSpec.scala`:

```scala
import zio.json.*
import zio.json.ast.Json
```

Keep the existing MUnit import.

- [ ] **Step 2: Add a JSON parser helper**

Add this helper inside `class CodecSpec`:

```scala
private def parseJson(input: String): Json =
    input.fromJson[Json] match
        case Right(value) => value
        case Left(error)  => fail(s"Invalid JSON: $error\n$input")
```

- [ ] **Step 3: Add exact Text wire-shape test**

Add this test:

```scala
test("Text JSON wire shape is stable") {
    val text = Text("hello", loc)
    val json = parseJson(AsgCodecs.encode(text: Node))
    val expected =
        parseJson(
            """{"name":"text","type":"string","value":"hello","location":[{"line":1,"col":1},{"line":1,"col":10}]}"""
        )
    assertEquals(json, expected)
}
```

- [ ] **Step 4: Add exact CharRef wire-shape test**

Add this test:

```scala
test("CharRef JSON wire shape uses charref discriminator") {
    val charRef = CharRef("&amp;", loc)
    val json = parseJson(AsgCodecs.encode(charRef: Node))
    val expected =
        parseJson(
            """{"name":"charref","type":"string","value":"&amp;","location":[{"line":1,"col":1},{"line":1,"col":10}]}"""
        )
    assertEquals(json, expected)
}
```

- [ ] **Step 5: Add DList and DListItem discriminator test**

Add this test:

```scala
test("DList JSON wire shape uses dlist and dlistItem discriminators") {
    val dlist = DList(
        marker = "::",
        items = Chunk(
            DListItem(
                marker = "::",
                terms = Chunk(Chunk(Text("term", loc))),
                principal = Some(Chunk(Text("definition", loc))),
                location = loc
            )
        ),
        location = loc
    )

    val json = AsgCodecs.encode(dlist: Node)
    assert(json.contains("\"name\":\"dlist\""), s"JSON should use dlist discriminator: $json")
    assert(json.contains("\"name\":\"dlistItem\""), s"JSON should use dlistItem discriminator: $json")
    assertEquals(AsgCodecs.decode(json), Right(dlist))
}
```

- [ ] **Step 6: Add inline-array wire-shape test**

Add this test:

```scala
test("encodeInlines returns a JSON array of inline nodes") {
    val inlines = Chunk[Inline](Text("one", loc), CharRef("&amp;", loc))
    val json = parseJson(AsgCodecs.encodeInlines(inlines))
    val expected =
        parseJson(
            """[
              |{"name":"text","type":"string","value":"one","location":[{"line":1,"col":1},{"line":1,"col":10}]},
              |{"name":"charref","type":"string","value":"&amp;","location":[{"line":1,"col":1},{"line":1,"col":10}]}
              |]""".stripMargin
        )
    assertEquals(json, expected)
}
```

- [ ] **Step 7: Run golden tests before migration**

Run:

```bash
./mill ascribe.asg.jvm.test
```

Expected: PASS. This proves the new tests capture current zio-blocks behavior before implementation changes.

- [ ] **Step 8: Commit golden tests**

Run:

```bash
git add ascribe/asg/test/src/io/eleven19/ascribe/asg/CodecSpec.scala
git commit -m "test(asg): pin ASG JSON wire shape"
```

---

### Task 2: Switch Build Dependency And Chunk Type

**Files:**
- Modify: `ascribe/asg/package.mill`
- Modify: `ascribe/asg/src/io/eleven19/ascribe/asg/*.scala`
- Modify: `ascribe/asg/test/src/io/eleven19/ascribe/asg/*.scala`
- Modify: `ascribe/bridge/src/io/eleven19/ascribe/bridge/*.scala`
- Modify: `ascribe/bridge/test/src/io/eleven19/ascribe/bridge/*.scala`

- [ ] **Step 1: Replace ASG dependency**

In `ascribe/asg/package.mill`, replace:

```scala
mvn"dev.zio::zio-blocks-schema::0.0.29"
```

with:

```scala
mvn"io.getkyo::kyo-schema::1.0.0-RC4"
```

- [ ] **Step 2: Replace Chunk imports**

Run:

```bash
rg -l "zio\\.blocks\\.chunk\\.Chunk" ascribe/asg ascribe/bridge | xargs perl -0pi -e 's/import zio\\.blocks\\.chunk\\.Chunk/import kyo.Chunk/g'
```

Expected: ASG and bridge code now import `kyo.Chunk`.

- [ ] **Step 3: Remove schema derivation from simple support files**

In `BlockMetadata.scala`, remove:

```scala
import zio.blocks.schema.Schema
```

and change:

```scala
) derives Schema
```

to:

```scala
)
```

Apply the same pattern to `Header.scala`, `Position.scala`, and `ColumnSpec.scala`.

- [ ] **Step 4: Remove schema derivation from `Node.scala`**

In `Node.scala`, replace:

```scala
import zio.blocks.schema.{Modifier, Schema}
```

with no schema import.

For each trait or case class in `Node.scala`, remove `derives Schema`.

For each private constructor field:

```scala
@Modifier.rename("type") nodeType: String
```

replace with:

```scala
nodeType: String
```

- [ ] **Step 5: Verify the expected compile failure**

Run:

```bash
./mill ascribe.asg.jvm.compile
```

Expected: FAIL in `AsgCodecs.scala`, because it still imports zio-blocks schema APIs. This is the intended red state before replacing the codec.

- [ ] **Step 6: Commit dependency and Chunk migration**

Run:

```bash
git add ascribe/asg/package.mill ascribe/asg/src ascribe/asg/test ascribe/bridge/src ascribe/bridge/test
git commit -m "build(asg): switch to kyo schema and chunk"
```

---

### Task 3: Add Explicit ASG Wire Tree Conversion

**Files:**
- Create: `ascribe/asg/src/io/eleven19/ascribe/asg/AsgWire.scala`

- [ ] **Step 1: Create `AsgWire.scala` scaffold**

Create `AsgWire.scala`:

```scala
package io.eleven19.ascribe.asg

import kyo.{Chunk, Result, Structure}
import kyo.Structure.Value

private[asg] object AsgWire:

    def toValue(node: Node): Value =
        node match
            case node: Document   => document(node)
            case node: Block      => block(node)
            case node: Inline     => inline(node)

    def fromValue(value: Value): Either[String, Node] =
        value match
            case Value.Record(fields) =>
                fieldString(fields, "name").flatMap(name => nodeFromRecord(name, fields))
            case other =>
                Left(s"Expected ASG node object, got $other")

    private def nodeFromRecord(name: String, fields: Chunk[(String, Value)]): Either[String, Node] =
        name match
            case "document" => documentFrom(fields)
            case name if blockNames.contains(name) => blockFrom(name, fields)
            case name if inlineNames.contains(name) => inlineFrom(name, fields)
            case other => Left(s"Unknown ASG node name: $other")

    private val blockNames: Set[String] =
        Set(
            "section", "heading", "paragraph", "listing", "literal", "pass", "stem", "verse",
            "sidebar", "example", "admonition", "open", "quote", "list", "dlist", "listItem",
            "dlistItem", "break", "table", "tableRow", "tableCell", "audio", "video", "image", "toc"
        )

    private val inlineNames: Set[String] =
        Set("span", "ref", "text", "charref", "raw")
```

- [ ] **Step 2: Add JSON value constructors**

Add helper methods to `AsgWire`:

```scala
    private def obj(fields: (String, Value)*): Value =
        Value.Record(Chunk.from(fields))

    private def arr(values: Chunk[Value]): Value =
        Value.Sequence(values)

    private def str(value: String): Value =
        Value.Str(value)

    private def int(value: Int): Value =
        Value.Integer(value.toLong)

    private def bool(value: Boolean): Value =
        Value.Bool(value)

    private def opt[A](name: String, value: Option[A])(f: A => Value): Chunk[(String, Value)] =
        value match
            case Some(v) => Chunk((name, f(v)))
            case None    => Chunk.empty

    private def nonEmpty[A](name: String, value: Chunk[A])(f: A => Value): Chunk[(String, Value)] =
        if value.isEmpty then Chunk.empty
        else Chunk((name, arr(value.map(f))))

    private def field(name: String, value: Value): Chunk[(String, Value)] =
        Chunk((name, value))

    private def record(fields: Chunk[(String, Value)]): Value =
        Value.Record(fields)
```

- [ ] **Step 3: Add discriminator-name mapping helpers**

Add these helpers:

```scala
    private def blockName(block: Block): String =
        block match
            case _: Section    => "section"
            case _: Heading    => "heading"
            case _: Paragraph  => "paragraph"
            case _: Listing    => "listing"
            case _: Literal    => "literal"
            case _: Pass       => "pass"
            case _: Stem       => "stem"
            case _: Verse      => "verse"
            case _: Sidebar    => "sidebar"
            case _: Example    => "example"
            case _: Admonition => "admonition"
            case _: Open       => "open"
            case _: Quote      => "quote"
            case _: List       => "list"
            case _: DList      => "dlist"
            case _: ListItem   => "listItem"
            case _: DListItem  => "dlistItem"
            case _: Break      => "break"
            case _: Table      => "table"
            case _: TableRow   => "tableRow"
            case _: TableCell  => "tableCell"
            case _: Audio      => "audio"
            case _: Video      => "video"
            case _: Image      => "image"
            case _: Toc        => "toc"

    private def inlineName(inline: Inline): String =
        inline match
            case _: Span    => "span"
            case _: Ref     => "ref"
            case _: Text    => "text"
            case _: CharRef => "charref"
            case _: Raw     => "raw"
```

- [ ] **Step 4: Add common field encoders**

Add these helpers:

```scala
    private def location(value: Location): Value =
        arr(Chunk(position(value.start), position(value.end)))

    private def position(value: Position): Value =
        record(
            field("line", int(value.line)) ++
                field("col", int(value.col)) ++
                opt("file", value.file)(files => arr(files.map(str)))
        )

    private def metadata(value: BlockMetadata): Value =
        record(
            nonEmptyMap("attributes", value.attributes, str) ++
                nonEmpty("options", value.options)(str) ++
                nonEmpty("roles", value.roles)(str) ++
                opt("location", value.location)(location)
        )

    private def nonEmptyMap[A](name: String, value: Map[String, A], f: A => Value): Chunk[(String, Value)] =
        if value.isEmpty then Chunk.empty
        else Chunk((name, Value.Record(Chunk.from(value.toSeq.map((k, v) => k -> f(v))))))

    private def blockBase(name: String, block: Block): Chunk[(String, Value)] =
        field("name", str(name)) ++
            field("type", str(block.nodeType)) ++
            opt("id", block.id)(str) ++
            opt("title", block.title)(inlines) ++
            opt("reftext", block.reftext)(inlines) ++
            opt("metadata", block.metadata)(metadata)

    private def inlines(value: Chunk[Inline]): Value =
        arr(value.map(inline))

    private def blocks(value: Chunk[Block]): Value =
        arr(value.map(block))
```

- [ ] **Step 5: Implement inline and document encoders**

Add:

```scala
    private def document(value: Document): Value =
        record(
            field("name", str("document")) ++
                field("type", str(value.nodeType)) ++
                opt("attributes", value.attributes)(attributes) ++
                opt("header", value.header)(header) ++
                nonEmpty("blocks", value.blocks)(block) ++
                field("location", location(value.location))
        )

    private def attributes(value: Map[String, Option[String]]): Value =
        Value.Record(Chunk.from(value.toSeq.map {
            case (key, Some(v)) => key -> str(v)
            case (key, None)    => key -> Value.Null
        }))

    private def header(value: Header): Value =
        record(
            opt("title", value.title)(inlines) ++
                nonEmpty("authors", value.authors)(author) ++
                opt("location", value.location)(location)
        )

    private def author(value: Author): Value =
        record(
            opt("fullname", value.fullname)(str) ++
                opt("initials", value.initials)(str) ++
                opt("firstname", value.firstname)(str) ++
                opt("middlename", value.middlename)(str) ++
                opt("lastname", value.lastname)(str) ++
                opt("address", value.address)(str)
        )

    private def inline(value: Inline): Value =
        value match
            case n: Span =>
                record(
                    field("name", str(inlineName(n))) ++
                        field("type", str(n.nodeType)) ++
                        field("variant", str(n.variant)) ++
                        field("form", str(n.form)) ++
                        nonEmpty("inlines", n.inlines)(inline) ++
                        field("location", location(n.location))
                )
            case n: Ref =>
                record(
                    field("name", str(inlineName(n))) ++
                        field("type", str(n.nodeType)) ++
                        field("variant", str(n.variant)) ++
                        field("target", str(n.target)) ++
                        nonEmpty("inlines", n.inlines)(inline) ++
                        field("location", location(n.location))
                )
            case n: Text =>
                record(field("name", str("text")) ++ field("type", str(n.nodeType)) ++ field("value", str(n.value)) ++ field("location", location(n.location)))
            case n: CharRef =>
                record(field("name", str("charref")) ++ field("type", str(n.nodeType)) ++ field("value", str(n.value)) ++ field("location", location(n.location)))
            case n: Raw =>
                record(field("name", str("raw")) ++ field("type", str(n.nodeType)) ++ field("value", str(n.value)) ++ field("location", location(n.location)))
```

- [ ] **Step 6: Implement block encoders**

Add `block(value: Block): Value` with an explicit case for every block class listed in `blockName`. Use `blockBase(blockName(n), n)` plus each type's fields.

Required field groups:

```scala
private def leafBlock(name: String, n: { def form: Option[String]; def delimiter: Option[String]; def inlines: Chunk[Inline]; def location: Location } & Block): Value =
    record(blockBase(name, n) ++ opt("form", n.form)(str) ++ opt("delimiter", n.delimiter)(str) ++ nonEmpty("inlines", n.inlines)(inline) ++ field("location", location(n.location)))
```

Use that pattern for `Paragraph`, `Listing`, `Literal`, `Pass`, `Stem`, and `Verse`.

For parent blocks, include `form`, `delimiter`, `blocks`, and `location` for `Sidebar`, `Example`, `Admonition`, `Open`, and `Quote`.

For list blocks, encode:

- `List`: `variant`, `marker`, `items`, `location`
- `DList`: `marker`, `items`, `location`
- `ListItem`: `marker`, `principal`, `blocks`, `location`
- `DListItem`: `marker`, `terms`, `principal`, `blocks`, `location`

For media and table blocks, encode their declared fields from `Node.scala` with the same field names used by the case class, plus `name`, `type`, base fields, and `location`.

- [ ] **Step 7: Add minimal decoding helpers**

Add helpers:

```scala
    private def fieldValue(fields: Chunk[(String, Value)], name: String): Either[String, Value] =
        fields.find(_._1 == name).map(_._2).toRight(s"Missing field: $name")

    private def fieldString(fields: Chunk[(String, Value)], name: String): Either[String, String] =
        fieldValue(fields, name).flatMap {
            case Value.Str(value) => Right(value)
            case other           => Left(s"Expected string field $name, got $other")
        }

    private def fieldInt(fields: Chunk[(String, Value)], name: String): Either[String, Int] =
        fieldValue(fields, name).flatMap {
            case Value.Integer(value) => Right(value.toInt)
            case other                => Left(s"Expected integer field $name, got $other")
        }

    private def optional[A](fields: Chunk[(String, Value)], name: String)(f: Value => Either[String, A]): Either[String, Option[A]] =
        fields.find(_._1 == name) match
            case Some((_, Value.Null)) => Right(None)
            case Some((_, value))      => f(value).map(Some(_))
            case None                  => Right(None)

    private def chunkOf[A](value: Value)(f: Value => Either[String, A]): Either[String, Chunk[A]] =
        value match
            case Value.Sequence(values) =>
                values.foldLeft[Either[String, Chunk[A]]](Right(Chunk.empty)) { (acc, next) =>
                    for
                        chunk <- acc
                        item <- f(next)
                    yield chunk.append(item)
                }
            case other => Left(s"Expected array, got $other")
```

- [ ] **Step 8: Implement decoders for golden-test nodes first**

Implement `locationFrom`, `positionFrom`, `inlineFrom`, and `blockFrom` cases for:

- `Text`
- `CharRef`
- `Span`
- `Paragraph`
- `Document`
- `DList`
- `DListItem`
- `List`
- `ListItem`

Run:

```bash
./mill ascribe.asg.jvm.test
```

Expected: tests compile and remaining decode tests identify any missing decode cases.

- [ ] **Step 9: Complete decoders for all node types**

Complete `blockFrom` and `inlineFrom` for every ASG node listed in `Node.scala`. Decode unknown fields by ignoring them. Decode missing fields with the same defaults as the public companion `apply` methods.

Run:

```bash
./mill ascribe.asg.jvm.test
```

Expected: PASS.

- [ ] **Step 10: Commit wire conversion**

Run:

```bash
git add ascribe/asg/src/io/eleven19/ascribe/asg/AsgWire.scala
git commit -m "feat(asg): add explicit kyo wire conversion"
```

---

### Task 4: Reimplement `AsgCodecs` With Kyo Json

**Files:**
- Modify: `ascribe/asg/src/io/eleven19/ascribe/asg/AsgCodecs.scala`

- [ ] **Step 1: Replace imports and codec implementation**

Replace `AsgCodecs.scala` with:

```scala
package io.eleven19.ascribe.asg

import kyo.{Chunk, Json, Structure}
import kyo.Structure.Value

/** JSON codec for ASG nodes. The JSON format matches the AsciiDoc TCK ASG shape with "name" as the node
  * discriminator and "type" as the node category field.
  */
object AsgCodecs:

    /** Encode an ASG Node to a JSON string. */
    def encode(node: Node): String =
        Json.encode(AsgWire.toValue(node))

    /** Encode a sequence of Inline nodes as a JSON array. Used for inline-only TCK tests. */
    def encodeInlines(inlines: Chunk[Inline]): String =
        Json.encode(Value.Sequence(inlines.map(inline => AsgWire.toValue(inline: Node))))

    /** Decode a JSON string to an ASG Node. */
    def decode(json: String): Either[String, Node] =
        Json.decode[Structure.Value](json).toEither.left.map(_.toString).flatMap(AsgWire.fromValue)
```

- [ ] **Step 2: Compile ASG**

Run:

```bash
./mill ascribe.asg.jvm.compile
```

Expected: PASS.

- [ ] **Step 3: Run ASG tests on JVM and JS**

Run:

```bash
./mill ascribe.asg.jvm.test ascribe.asg.js.test
```

Expected: PASS.

- [ ] **Step 4: Commit codec replacement**

Run:

```bash
git add ascribe/asg/src/io/eleven19/ascribe/asg/AsgCodecs.scala
git commit -m "feat(asg): encode ASG JSON with kyo schema"
```

---

### Task 5: Fix Bridge/TCK Fallout And Remove Residual Zio Blocks Schema Usage

**Files:**
- Modify as needed:
  - `ascribe/bridge/src/io/eleven19/ascribe/bridge/AstToAsg.scala`
  - `ascribe/bridge/src/io/eleven19/ascribe/bridge/ColsParser.scala`
  - `ascribe/bridge/test/src/io/eleven19/ascribe/bridge/AstToAsgSpec.scala`
  - `ascribe/tck-runner/test/src/build/ascribe/tckrunner/TckSteps.scala`

- [ ] **Step 1: Compile bridge**

Run:

```bash
./mill ascribe.bridge.jvm.compile ascribe.bridge.js.compile
```

Expected: PASS or compile errors limited to `Chunk` API differences.

- [ ] **Step 2: Fix Kyo Chunk API differences**

For any compile error caused by collection construction:

- replace `Chunk.fromIterable(xs)` with `Chunk.from(xs)`
- replace collection builders with `Chunk.newBuilder[A]` where needed
- keep existing immutable collection flow; do not introduce mutable shared state

- [ ] **Step 3: Run bridge tests**

Run:

```bash
./mill ascribe.bridge.jvm.test ascribe.bridge.js.test
```

Expected: PASS.

- [ ] **Step 4: Run TCK runner tests**

Run:

```bash
./mill ascribe.tck-runner.test
```

Expected: PASS.

- [ ] **Step 5: Verify residual zio-blocks scope**

Run:

```bash
rg -n "zio\\.blocks|zio-blocks-schema" ascribe build.mill.yaml mill-build -S -g '!ascribe/pipeline/markdown/**'
```

Expected: no matches. Matches under `ascribe/pipeline/markdown/**` are allowed in this PR.

- [ ] **Step 6: Commit fallout fixes**

Run:

```bash
git add ascribe/bridge ascribe/tck-runner ascribe/asg
git commit -m "refactor(asg): remove zio blocks usage"
```

---

### Task 6: Full Verification

**Files:**
- No planned source edits.

- [ ] **Step 1: Resolve build graph**

Run:

```bash
./mill resolve __
```

Expected: SUCCESS.

- [ ] **Step 2: Compile everything**

Run:

```bash
./mill __.compile
```

Expected: SUCCESS.

- [ ] **Step 3: Test everything**

Run:

```bash
./mill __.test
```

Expected: SUCCESS.

- [ ] **Step 4: Run CI lint target**

Run:

```bash
./mill ascribe.core.jvm.checkFormat
```

Expected: SUCCESS.

- [ ] **Step 5: Run diff whitespace check**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 6: Re-run residual usage check**

Run:

```bash
rg -n "zio\\.blocks|zio-blocks-schema" ascribe build.mill.yaml mill-build -S -g '!ascribe/pipeline/markdown/**'
```

Expected: no matches.

- [ ] **Step 7: Commit verification-only doc adjustments if any**

If implementation required small docs updates outside historical design docs, commit them:

```bash
git add README.md docs/_docs
git commit -m "docs: update ASG schema implementation notes"
```

If no docs changed, skip this step.

---

### Task 7: Publish PR And Standard Landing Routine

**Files:**
- No planned source edits.

- [ ] **Step 1: Check clean status**

Run:

```bash
git status -sb
```

Expected: clean branch tracking `origin/feat/kyo-schema-migration`.

- [ ] **Step 2: Push branch**

Run:

```bash
git push -u origin feat/kyo-schema-migration
```

Expected: push succeeds.

- [ ] **Step 3: Open PR**

Use this title:

```text
refactor(asg): migrate schema codec to kyo-schema
```

Use this PR body:

```markdown
## Summary
- Replace ASG `zio-blocks-schema` usage with kyo-schema-backed JSON encoding.
- Replace ASG/bridge `zio.blocks.chunk.Chunk` usage with `kyo.Chunk`.
- Preserve the current ASG/TCK JSON wire shape with explicit golden tests.
- Leave `ascribe/pipeline/markdown` and `zio-blocks-docs` untouched for now.

## Notes
- Kyo does not currently expose zio-blocks-schema-style variant name mapping for discriminator encoding, so `AsgCodecs` owns ASG discriminator names explicitly.
- Upstream Kyo issue: https://github.com/getkyo/kyo/issues/1691
- Tracks Beads issue `ascribe-3p8`.

## Verification
- `./mill resolve __`
- `./mill __.compile`
- `./mill __.test`
- `./mill ascribe.core.jvm.checkFormat`
- `git diff --check`
- `rg -n "zio\\.blocks|zio-blocks-schema" ascribe build.mill.yaml mill-build -S -g '!ascribe/pipeline/markdown/**'` (no matches)
```

- [ ] **Step 4: Monitor PR**

Run:

```bash
gh pr checks --watch --interval 30
```

If CI fails, inspect logs, fix on the branch, rerun local targeted verification, push, and continue monitoring.

- [ ] **Step 5: Check PR comments**

Run:

```bash
gh pr view --comments
```

Address actionable comments before merging.

- [ ] **Step 6: Squash merge on green**

After all checks are green and comments are addressed:

```bash
gh pr merge --squash --delete-branch --subject "refactor(asg): migrate schema codec to kyo-schema" --body "Replaces ASG zio-blocks-schema usage with kyo-schema-backed JSON encoding and migrates ASG/bridge collections to kyo.Chunk."
```

If local worktree checkout cleanup fails because `main` is already checked out in the root worktree, verify the PR merged remotely and perform cleanup manually from the root checkout.

- [ ] **Step 7: Sync main, close Beads, cleanup worktree**

From `/Users/damian/code/github/Eleven19/ascribe`:

```bash
git fetch origin --prune
git pull --ff-only
git worktree unlock .worktrees/kyo-schema-migration || true
git worktree remove .worktrees/kyo-schema-migration
git branch -D feat/kyo-schema-migration || true
bd close ascribe-3p8 --reason "Merged kyo-schema migration PR" --json
bd dolt push
git pull --rebase
git push
git status -sb
```

Expected: root `main` is clean and up to date with `origin/main`.
