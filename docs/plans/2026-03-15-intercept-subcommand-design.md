# Intercept Subcommand Design

## Goal

Replace environment-variable-driven tool selection with an explicit CLI shape
centered on `mill-interceptor intercept <tool> ...`.

## Scope

This design covers:

- top-level CLI behavior for `mill-interceptor`
- dispatch from `intercept` to the existing Maven, sbt, and Gradle interceptors
- help and error behavior at the top-level parser boundary
- testing and documentation changes required for the new command shape

This design does not cover:

- preserving `INTERCEPTED_BUILD_TOOL` as a fallback
- keeping old direct forms like `mill-interceptor maven ...`
- adding any new top-level commands beyond `intercept`

## Constraints

- The current implementation reads `INTERCEPTED_BUILD_TOOL` in
  [Main.scala](/home/damian/code/repos/github/Eleven19/mill-interceptor/src/io/github/eleven19/mill/interceptor/Main.scala)
  and dispatches to the existing `Mvn`, `Sbt`, and `Gradle` modules.
- Each tool already has parser and mapping coverage in the current test suite, so
  the safest change is at the CLI boundary rather than inside the tool-specific
  parsing logic.
- The new CLI must support `mvn` as an alias for Maven.
- The new CLI must require the explicit parent command because more top-level
  commands are planned later.
- Running `mill-interceptor` incorrectly should show help rather than fail with a
  bare parser error.

## Recommended Approach

Add a small dedicated top-level CLI parser and keep the existing tool modules
responsible for their current argument handling and Mill invocation.

The parser should:

- recognize `intercept` as the only supported top-level command for interception
- accept `maven`, `mvn`, `sbt`, and `gradle` as tool selectors
- preserve the remaining raw arguments without reinterpretation
- return a structured result that makes help and error behavior easy to test

This keeps the change localized, supports future top-level commands cleanly, and
avoids unnecessary churn in the existing Maven, sbt, and Gradle parsers.

## CLI Contract

The only supported interception entrypoint becomes:

```bash
mill-interceptor intercept <maven|mvn|sbt|gradle> [tool-specific args...]
```

Behavior:

- `mill-interceptor intercept maven ...` dispatches to the Maven interceptor
- `mill-interceptor intercept mvn ...` dispatches to the Maven interceptor
- `mill-interceptor intercept sbt ...` dispatches to the sbt interceptor
- `mill-interceptor intercept gradle ...` dispatches to the Gradle interceptor
- `mill-interceptor` with no subcommand shows help
- unknown top-level commands show help
- `intercept` without a tool shows `intercept` usage help
- unknown tools fail with a clear message and supported values
- `INTERCEPTED_BUILD_TOOL` is no longer read
- direct forms like `mill-interceptor maven ...` are invalid

## Component Design

Add a small parser module under
[src/io/github/eleven19/mill/interceptor](/home/damian/code/repos/github/Eleven19/mill-interceptor/src/io/github/eleven19/mill/interceptor),
for example `Cli.scala`.

That module should define:

- a small ADT for supported tools
- a small ADT for parse results, including help/error states
- a pure `parse(args: Chunk[String])` entrypoint
- a stable usage string for help output

[Main.scala](/home/damian/code/repos/github/Eleven19/mill-interceptor/src/io/github/eleven19/mill/interceptor/Main.scala)
then becomes a thin dispatcher that:

- parses the top-level CLI once
- prints help when requested or when the command is invalid
- routes `Run(Maven, args)` to `Mvn.run(args)`
- routes `Run(Sbt, args)` to `Sbt.run(args)`
- routes `Run(Gradle, args)` to `Gradle.run(args)`

The existing tool modules remain unchanged unless a small signature cleanup makes
the dispatch path clearer.

## Testing Strategy

Add focused tests around the new parser boundary rather than duplicating current
tool-specific tests.

Required coverage:

- parse `intercept maven clean`
- parse `intercept mvn clean`
- parse `intercept sbt compile`
- parse `intercept gradle build`
- preserve forwarded raw arguments exactly
- return help for no command
- return help or usage error for missing tool
- return a clear error for unknown top-level command
- return a clear error for unknown tool

Existing Maven, sbt, and Gradle tests remain the regression net for forwarded
arguments after dispatch.

## Documentation and Release Notes

Update:

- [README.md](/home/damian/code/repos/github/Eleven19/mill-interceptor/README.md)
  so the documented invocation uses `mill-interceptor intercept <tool> ...`
- [CHANGELOG.md](/home/damian/code/repos/github/Eleven19/mill-interceptor/CHANGELOG.md)
  under `[Unreleased]` to record the CLI contract change

This is a user-visible behavior change, so it should be captured in the
changelog as part of the implementation.
