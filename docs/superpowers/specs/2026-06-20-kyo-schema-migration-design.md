# Kyo Schema Migration Design

## Goal

Replace Ascribe's ASG use of `zio-blocks-schema` and `zio.blocks.chunk.Chunk` with `kyo-schema` and `kyo.Chunk`, while preserving the existing ASG JSON contract used by the AsciiDoc TCK and downstream renderers.

## Scope

In scope:

- Replace `dev.zio::zio-blocks-schema::0.0.29` in `ascribe/asg`.
- Replace `zio.blocks.chunk.Chunk` with `kyo.Chunk` in ASG, bridge, TCK-facing code, and related tests.
- Keep `AsgCodecs` public API stable:
  - `encode(node: Node): String`
  - `encodeInlines(inlines: Chunk[Inline]): String`
  - `decode(json: String): Either[String, Node]`
- Preserve current ASG JSON wire shape, including:
  - flat `"name"` discriminator field
  - `"type"` node category field
  - TCK-specific discriminator names such as `"dlist"`, `"dlistItem"`, and `"charref"`
  - `Location` serialized as `[start, end]`
  - omitted transient/default fields where current JSON omits them

Out of scope for this PR:

- `ascribe/pipeline/markdown` and `dev.zio::zio-blocks-docs::0.0.29`.
- Broad documentation rewrites for historical design docs that describe the old implementation.
- Changing ASG model semantics or adding new AsciiDoc coverage.

## Current State

The ASG module currently derives JSON codecs with `zio-blocks-schema`:

- ASG types in `ascribe/asg/src/io/eleven19/ascribe/asg` use `derives Schema`.
- `AsgCodecs` uses `JsonBinaryCodecDeriver` with `DiscriminatorKind.Field("name")`.
- `AsgCodecs` also uses `NameMapper.Custom` to map Scala case names to TCK names.
- `Location` has a custom schema transform that encodes it as a two-element array.
- ASG and bridge code use `zio.blocks.chunk.Chunk`.

The current codec tests verify round trips and a few discriminator names, but they are not precise golden tests for every wire-shape detail this migration can accidentally change.

## Chosen Approach

Use an explicit ASG wire codec backed by `kyo-schema`, rather than relying on default sealed-trait derivation alone.

The migration should still use `kyo.Schema` and `kyo.Json` as the serialization foundation, but `AsgCodecs` must own the ASG/TCK wire contract explicitly. This avoids depending on undocumented or absent variant-name mapping behavior in Kyo's default ADT codec.

Kyo-schema supports:

- `derives Schema`
- `Json.encode` / `Json.decode`
- `Schema.discriminator("field")`
- `Schema.rename`, `Schema.drop`, `Schema.add`, and `Schema.transform`
- `Schema.init` for explicit schemas
- built-in `Schema[Chunk[A]]`

The high-risk gap compared to the current implementation is variant name mapping. Kyo-schema's discriminator support uses Scala variant names by default. Ascribe needs lower/camel/special names defined by the ASG/TCK wire contract. Therefore, `AsgCodecs` should avoid a blind `Schema[Node].discriminator("name")` swap unless tests prove exact output parity.

## Architecture

### Domain Model

The ASG domain model remains the same shape:

- `Node`, `Block`, and `Inline` remain sealed traits.
- Existing case classes remain the public model.
- Constructors with computed `"type"` values remain valid unless the implementation chooses a clearer internal representation.
- Collections migrate from `zio.blocks.chunk.Chunk` to `kyo.Chunk`.

The domain model should not grow JSON-specific helper fields solely to satisfy the codec unless that is demonstrably cleaner than a separate wire layer.

### Codec Layer

`AsgCodecs` becomes the boundary between the public ASG model and the ASG/TCK JSON wire format.

The codec layer should:

- encode/decode through `kyo.Json`
- preserve `Either[String, Node]` for decode errors
- encode inline arrays by encoding each inline node as a node JSON object
- keep discriminator mapping in one local table or set of functions
- keep field rename rules in one local place, especially `nodeType` <-> `"type"`
- preserve `Location` array encoding with a Kyo schema transform or explicit schema

Acceptable implementation shapes:

- explicit `given Schema[...]` instances for ASG model types using `Schema.init`
- a private wire ADT with `derives Schema`, plus total conversion functions to/from the public ASG model
- a focused combination of derivation for product fields plus explicit handling for `Node`, `Block`, and `Inline` union dispatch

The implementation should prefer the smallest approach that passes golden JSON tests and leaves the mapping readable.

### Chunk Migration

All ASG and bridge imports of `zio.blocks.chunk.Chunk` should become `kyo.Chunk`.

Expected affected areas:

- `ascribe/asg/src/io/eleven19/ascribe/asg/*.scala`
- `ascribe/asg/test/src/io/eleven19/ascribe/asg/*.scala`
- `ascribe/bridge/src/io/eleven19/ascribe/bridge/*.scala`
- `ascribe/bridge/test/src/io/eleven19/ascribe/bridge/*.scala`
- `ascribe/tck-runner/test/src/build/ascribe/tckrunner/TckSteps.scala` if type inference or imports require it

The migration should rely on Kyo `Chunk`'s standard collection methods where possible. Avoid adding compatibility wrappers unless real API differences force them.

### Build Dependencies

`ascribe/asg/package.mill` should replace:

```scala
mvn"dev.zio::zio-blocks-schema::0.0.29"
```

with:

```scala
mvn"io.getkyo::kyo-schema::1.0.0-RC4"
```

No markdown dependency changes should be made in this PR.

## Test Strategy

Tests must be strengthened before implementation changes.

Add golden tests in `ascribe/asg/test/src/io/eleven19/ascribe/asg/CodecSpec.scala` for:

- `Text` exact JSON shape, including `"name":"text"` and `"type":"string"`.
- `CharRef` exact discriminator `"charref"`.
- `DList` exact discriminator `"dlist"` and nested `DListItem` discriminator `"dlistItem"`.
- `Location` exact array shape.
- `Document` with nested blocks and omitted default/transient fields matching current behavior.
- `encodeInlines` returning a JSON array of inline node objects.
- Decode of at least one golden node object back to the expected ASG value.

The first implementation checkpoint should run these tests against the existing zio-blocks implementation to prove the goldens capture current behavior.

After migration, run:

```bash
./mill ascribe.asg.jvm.test
./mill ascribe.asg.js.test
./mill ascribe.bridge.jvm.test
./mill ascribe.bridge.js.test
./mill ascribe.tck-runner.test
./mill __.compile
./mill __.test
./mill ascribe.core.jvm.checkFormat
git diff --check
```

Also verify no prohibited dependencies remain outside the explicitly excluded markdown module:

```bash
rg -n "zio\\.blocks|zio-blocks-schema" ascribe build.mill.yaml mill-build -S \
  -g '!ascribe/pipeline/markdown/**'
```

Expected result: no matches.

## Risks

### Variant names

The biggest migration risk is changing ASG discriminator names. Kyo-schema's default sealed-trait discriminator would emit Scala case names such as `"DList"` unless explicitly handled. Golden tests must fail if this happens.

### Default field emission

The current zio-blocks codec uses transient default handling. Kyo-schema may emit defaults differently unless schemas or wire types are shaped to match. Golden tests should pin the behavior that matters for TCK comparison.

### Decode compatibility

Encoding is the primary path for TCK output, but existing tests call `AsgCodecs.decode`. The migration should preserve decode support for the ASG JSON shape that Ascribe emits and TCK fixtures expect.

### Scala.js compatibility

ASG already cross-compiles. Kyo-schema is published for the project Kyo version, but JVM-only APIs or accidental Java dependencies in custom codec code would break Scala.js. Run ASG JS tests early and often.

## Non-Goals

- Do not migrate the Markdown pipeline off `zio-blocks-docs` in this PR.
- Do not change public package names.
- Do not change `AsgCodecs` callers.
- Do not rewrite historical planning docs except for small references if needed.
- Do not introduce another JSON library for ASG unless kyo-schema cannot preserve the wire contract after a focused attempt.
