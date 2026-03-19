---
title: TCK Compliance
---

# TCK Compliance

## What is the TCK?

The AsciiDoc Technology Compatibility Kit (TCK) is the official test suite maintained by the Eclipse AsciiDoc Language project. It defines expected behavior for AsciiDoc processors through a set of input/output test cases.

The TCK repository is at [gitlab.eclipse.org/eclipse/asciidoc-lang/asciidoc-tck](https://gitlab.eclipse.org/eclipse/asciidoc-lang/asciidoc-tck).

## How TCK Tests Work

Each TCK test case consists of:

1. **Feature file** (`.feature`) -- A Cucumber scenario describing the test.
2. **Input file** (`input.adoc`) -- The AsciiDoc source to parse.
3. **Expected output** (`output.json`) -- The expected ASG as JSON.

The test flow is:

```
input.adoc
  --> Ascribe.parse()        (Source -> AST)
  --> AstToAsg.convert()     (AST -> ASG)
  --> AsgCodecs.encode()     (ASG -> JSON)
  --> compare with output.json
```

JSON comparison is structural (parsed as `zio.json.ast.Json`), so field ordering does not matter.

For inline-only tests, the expected JSON is an array rather than an object. In these cases the runner extracts inlines from the first paragraph and encodes them with `AsgCodecs.encodeInlines`.

## Current Status

**78 out of 78 test scenarios passing**, including 22 official TCK test cases and 28 custom table test scenarios.

Custom table tests live in `ascribe/tck-runner/test/resources/custom-tests/tables/` and cover PSV, CSV, and DSV formats, column specs, cell specifiers, header/footer rows, and nested tables.

## Test Runner Implementation

The TCK runner lives in `ascribe/tck-runner/test/src/build/ascribe/tckrunner/`:

- **`TckSuite.scala`** -- JUnit Platform suite that discovers Cucumber features on the classpath.
- **`TckSteps.scala`** -- Step definitions implementing the Given/When/Then flow:
  - `Given the AsciiDoc input from {file}` -- reads the input file
  - `When the input is parsed` -- runs `Ascribe.parse` then `AstToAsg.convert`
  - `Then the resulting ASG should match the expected JSON in {file}` -- encodes to JSON and compares

## Running TCK Tests

Refresh test data from the submodule, then run:

```bash
./mill ascribe.tck-runner.tckRefresh
./mill ascribe.tck-runner.test
```

## Adding New TCK Coverage

When new TCK test cases are added to the upstream TCK repository:

1. Update the `submodules/asciidoc-tck` submodule to the latest version.
2. Run `./mill ascribe.tck-runner.tckRefresh` to copy the new feature files and test data.
3. Run `./mill ascribe.tck-runner.test` to see which new tests pass or fail.
4. For failing tests, implement the missing parser/bridge/ASG support:
   - Add new AST node types in `ascribe/src/io/eleven19/ascribe/ast/Document.scala`
   - Add parser support in `BlockParser.scala` or `InlineParser.scala`
   - Add bridge conversion in `AstToAsg.scala`
   - Add any new ASG types in `Node.scala`
5. Re-run the TCK tests until all pass.

## Inspecting Test Failures

When a test fails, the runner prints a diff showing:

```
ASG JSON mismatch.

=== Expected ===
{ ... expected JSON ... }

=== Actual ===
{ ... actual JSON ... }
```

Compare the two to identify missing fields, incorrect values, or structural differences.
