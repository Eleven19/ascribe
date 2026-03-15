# Intercept Subcommand Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace environment-variable-driven interceptor selection with an explicit `mill-interceptor intercept <tool> ...` CLI, including `mvn` alias support, top-level help behavior, and updated docs/tests.

**Architecture:** Add a small pure top-level CLI parser that recognizes `intercept`, normalizes the selected tool, and preserves forwarded raw args. Keep the existing Maven, sbt, and Gradle modules responsible for their current parsing and Mill execution, and make `Main` a thin dispatcher over the new parser result.

**Tech Stack:** Scala 3, Kyo, case-app already present in the repo, Mill, zio-test via `KyoSpecDefault`

---

### Task 1: Add a Small Top-Level CLI Parser

**Files:**
- Create: `src/io/github/eleven19/mill/interceptor/Cli.scala`
- Create: `test/src/io/github/eleven19/mill/interceptor/CliSpec.scala`
- Test: `./mill test`

**Step 1: Write the failing parser tests**

Create `test/src/io/github/eleven19/mill/interceptor/CliSpec.scala` with focused coverage for:

```scala
test("parse intercept maven forwards remaining args") {
  val parsed = Cli.parse(Chunk("intercept", "maven", "clean", "test"))
  assertTrue(parsed == CliResult.Run(InterceptTool.Maven, Chunk("clean", "test")))
}

test("parse intercept mvn accepts the alias") {
  val parsed = Cli.parse(Chunk("intercept", "mvn", "install"))
  assertTrue(parsed == CliResult.Run(InterceptTool.Maven, Chunk("install")))
}

test("parse empty args returns help") {
  val parsed = Cli.parse(Chunk.empty)
  assertTrue(parsed == CliResult.Help(None))
}

test("parse intercept without a tool returns usage error") {
  val parsed = Cli.parse(Chunk("intercept"))
  assertTrue(parsed == CliResult.Help(Some("Missing intercept tool")))
}
```

**Step 2: Run the tests to verify they fail**

Run:

```bash
./mill test
```

Expected: FAIL because `Cli`, `CliResult`, and `InterceptTool` do not exist yet.

**Step 3: Add the minimal parser implementation**

Create `src/io/github/eleven19/mill/interceptor/Cli.scala` with a small pure model:

```scala
package io.github.eleven19.mill.interceptor

import kyo.*

enum InterceptTool:
  case Maven, Sbt, Gradle

enum CliResult:
  case Run(tool: InterceptTool, forwardedArgs: Chunk[String])
  case Help(error: Option[String])

object Cli:
  val usage: String =
    """Usage:
      |  mill-interceptor intercept <maven|mvn|sbt|gradle> [args...]
      |""".stripMargin

  def parse(args: Chunk[String]): CliResult =
    args.toList match
      case Nil => CliResult.Help(None)
      case "-h" :: _ | "--help" :: _ => CliResult.Help(None)
      case "intercept" :: Nil => CliResult.Help(Some("Missing intercept tool"))
      case "intercept" :: tool :: rest =>
        tool match
          case "maven" | "mvn" => CliResult.Run(InterceptTool.Maven, Chunk.from(rest))
          case "sbt"           => CliResult.Run(InterceptTool.Sbt, Chunk.from(rest))
          case "gradle"        => CliResult.Run(InterceptTool.Gradle, Chunk.from(rest))
          case other           => CliResult.Help(Some(s"Unsupported intercept tool: $other"))
      case command :: _ =>
        CliResult.Help(Some(s"Unsupported command: $command"))
```

Keep the parser small and pure. Do not reparse forwarded tool-specific arguments
here.

**Step 4: Run the tests to verify the parser passes**

Run:

```bash
./mill test
```

Expected: PASS for the new parser tests and no regressions in the existing suite.

**Step 5: Commit**

```bash
git add src/io/github/eleven19/mill/interceptor/Cli.scala test/src/io/github/eleven19/mill/interceptor/CliSpec.scala
git commit -m "feat: add top-level interceptor cli parser"
```

### Task 2: Wire `Main` to the Parser and Remove Env-Var Dispatch

**Files:**
- Modify: `src/io/github/eleven19/mill/interceptor/Main.scala`
- Test: `./mill test`

**Step 1: Add a failing regression for the new dispatch contract**

Extend `test/src/io/github/eleven19/mill/interceptor/CliSpec.scala` or add a small
`Main`-level test that asserts the parser result is the only source of tool
selection:

```scala
test("top-level parser does not require INTERCEPTED_BUILD_TOOL") {
  val parsed = Cli.parse(Chunk("intercept", "gradle", "build"))
  assertTrue(parsed == CliResult.Run(InterceptTool.Gradle, Chunk("build")))
}
```

This test should already compile but the repo behavior is still wrong until
`Main.scala` stops consulting `INTERCEPTED_BUILD_TOOL`.

**Step 2: Run the tests or compile check before changing `Main`**

Run:

```bash
./mill test.compile
```

Expected: PASS for the test source compilation, while the application still uses
the old env-var-driven dispatcher.

**Step 3: Replace the env-var path in `Main.scala`**

Update `Main.scala` so it parses the top-level CLI and dispatches directly:

```scala
run {
  Log.let(scribeLog) {
    direct {
      Cli.parse(args) match
        case CliResult.Run(InterceptTool.Maven, forwardedArgs) =>
          Mvn.run(forwardedArgs).now
        case CliResult.Run(InterceptTool.Sbt, forwardedArgs) =>
          Sbt.run(forwardedArgs).now
        case CliResult.Run(InterceptTool.Gradle, forwardedArgs) =>
          Gradle.run(forwardedArgs).now
        case CliResult.Help(error) =>
          error.foreach(message => Log.error(message).now)
          println(Cli.usage)
          if error.isDefined then
            Abort.fail(new IllegalArgumentException(error.get)).now
    }
  }
}
```

Important details:

- remove all reads of `INTERCEPTED_BUILD_TOOL`
- do not allow direct top-level `maven`, `mvn`, `sbt`, or `gradle` commands
- keep help output stable and explicit
- preserve the existing tool runner behavior after dispatch

**Step 4: Run the test suite to verify the new entrypoint**

Run:

```bash
./mill test
```

Expected: PASS, with the parser tests covering the new contract and existing tool
tests still green.

**Step 5: Commit**

```bash
git add src/io/github/eleven19/mill/interceptor/Main.scala test/src/io/github/eleven19/mill/interceptor/CliSpec.scala
git commit -m "feat: route interceptor execution through intercept subcommand"
```

### Task 3: Update User-Facing Documentation and Changelog

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Test: `rg -n "intercept <maven\\|mvn\\|sbt\\|gradle>|INTERCEPTED_BUILD_TOOL" README.md CHANGELOG.md src`

**Step 1: Write the documentation changes**

Update `README.md` so usage examples and capability descriptions refer to the new
entrypoint. The resulting section should look like:

```markdown
## Usage

- `mill-interceptor intercept mvn test`
- `mill-interceptor intercept sbt compile`
- `mill-interceptor intercept gradle build`
```

Update `CHANGELOG.md` under `[Unreleased]`:

```markdown
### Changed

- Changed interceptor selection to use `mill-interceptor intercept <tool> ...`
  instead of `INTERCEPTED_BUILD_TOOL`.

### Documentation

- Documented the `intercept` subcommand and `mvn` alias behavior.
```

**Step 2: Run a focused verification search**

Run:

```bash
rg -n "intercept <maven\\|mvn\\|sbt\\|gradle>|INTERCEPTED_BUILD_TOOL" README.md CHANGELOG.md src
```

Expected:

- README contains the new invocation text
- CHANGELOG contains the unreleased change note
- source no longer references `INTERCEPTED_BUILD_TOOL`

**Step 3: Run the full test suite as the final quality gate**

Run:

```bash
./mill test
```

Expected: PASS.

**Step 4: Review the working tree**

Run:

```bash
git status --short
```

Expected: only the planned source, test, and documentation changes are present.

**Step 5: Commit**

```bash
git add README.md CHANGELOG.md
git commit -m "docs: document intercept subcommand cli"
```
