# TCK Mill Meta-Build Tasks Design

**Date:** 2026-03-16
**Issue:** ascribe-bkq — Move TCK sync/generation to mill meta-build tasks

## Problem

`GenerateTckFeatures.scala` lives inside `ascribe/tck-runner/src/` as a standalone `main` class. Submodule sync and feature file generation are build concerns, not runtime code. The tck-runner module should focus purely on test execution. Additionally, generated feature files are checked into git, which can drift out of sync with the TCK submodule.

## Design

Three components in the mill meta-build, following the existing `PublishSupport` pattern:

### Trait 1: `TckSubmoduleSupport`

**File:** `mill-build/src/build/TckSubmoduleSupport.scala`

Manages TCK git submodule lifecycle.

```scala
trait TckSubmoduleSupport extends Module {
  /** Path to the TCK git submodule, relative to workspace root */
  def tckSubmodulePath: T[os.Path]

  /** Initialize/update the TCK submodule. Returns path to checked-out submodule. */
  def tckSync: T[PathRef] = Task {
    val submodulePath = tckSubmodulePath()
    os.proc("git", "submodule", "update", "--init", submodulePath)
      .call(cwd = Task.workspace)
    PathRef(submodulePath)
  }
}
```

- `tckSubmodulePath` is abstract — consuming modules configure it
- `tckSync` runs `git submodule update --init` and returns a `PathRef` for cache invalidation

### Trait 2: `TckTestSupport`

**File:** `mill-build/src/build/TckTestSupport.scala`

Generates Cucumber feature files from TCK test data and merges with custom features.

```scala
trait TckTestSupport extends Module {
  /** Path to TCK test data root (containing tests/block/, tests/inline/) */
  def tckTestDataPath: T[os.Path]

  /** Hand-written feature files to merge with generated ones. Override to provide. */
  def customTckFeatures: T[Seq[PathRef]] = Task { Seq.empty[PathRef] }

  /** Generate .feature files from TCK test data */
  def generateTckFeatures: T[PathRef] = Task {
    val tckDir = tckTestDataPath() / "tests"
    val outDir = Task.dest / "features"
    os.makeDir.all(outDir)
    // Scan block/ and inline/ subdirectories for *-input.adoc files
    // Generate corresponding .feature files with Scenario Outline format
    // (logic ported from current GenerateTckFeatures.scala)
    PathRef(Task.dest)
  }

  /** Merged features: generated + custom */
  def tckFeatures: T[Seq[PathRef]] = Task {
    Seq(generateTckFeatures()) ++ customTckFeatures()
  }
}
```

- `tckTestDataPath` is abstract — wired to `tckSync` output by the consuming module
- `customTckFeatures` defaults to empty, override to include hand-written scenarios
- `generateTckFeatures` writes into `Task.dest`, not the source tree
- `tckFeatures` merges generated and custom features into a single sequence

### Top-Level Command: `tck.mill`

**File:** `tck.mill` (root-level, next to `build.mill.yaml`)

Provides a convenience command that runs both sync and generation:

```scala
import mill._

object tck extends Module {
  def refresh() = Task.Command {
    // Delegates to tckSync then generateTckFeatures
  }
}
```

Invoked as: `./mill tck.refresh`

## Data Flow

```
./mill tck.refresh
    +-- tckSync (TckSubmoduleSupport)
    |   runs: git submodule update --init submodules/tck
    |   returns: PathRef to submodules/tck/
    |
    +-- generateTckFeatures (TckTestSupport)
        input: tckTestDataPath (wired to tckSync output)
        scans: {tckTestDataPath}/tests/block/*/  and  tests/inline/*/
        generates: .feature files into Task.dest
        returns: PathRef to Task.dest

./mill ascribe.tck-runner.test
    +-- resources includes:
        +-- tckFeatures (TckTestSupport)
        |   +-- generateTckFeatures output (generated from TCK submodule)
        |   +-- customTckFeatures (hand-written, from test/resources/features/)
        +-- junit-platform.properties (stays in source tree)
```

Mill's task caching means `tckSync` won't re-run unless inputs change. Running `./mill ascribe.tck-runner.test` triggers the full chain automatically since `resources` depends on `tckFeatures`.

## Module Wiring

- `ascribe/tck-runner/package.mill.yaml` — adds `TckSubmoduleSupport` to extends, configures `tckSubmodulePath`
- `ascribe/tck-runner/test/package.mill.yaml` — adds `TckTestSupport` to extends, wires `tckTestDataPath` to parent's sync output, overrides `customTckFeatures` for hand-written features, overrides `resources` to include `tckFeatures` output

## Cleanup

### Removed

- `ascribe/tck-runner/src/build/ascribe/tckrunner/GenerateTckFeatures.scala` — logic moves into `TckTestSupport`
- `ascribe/tck-runner/test/resources/features/*.feature` (7 generated files) — generated into `Task.dest` at build time
- `os-lib` dependency on `tck-runner` main module (only needed for `GenerateTckFeatures`)

### Retained

- `ascribe/tck-runner/test/src/` — `TckSteps.scala` and Cucumber suite runner (test code)
- `ascribe/tck-runner/test/resources/junit-platform.properties` — Cucumber config
- `ascribe/tck-runner/test/resources/features/` — repurposed for hand-written custom features (initially empty with `.gitkeep`)

## Key Decisions

- **Configurable paths**: `tckSubmodulePath` and `tckTestDataPath` are abstract, not hardcoded
- **Generated files not checked in**: Feature files are build artifacts in `Task.dest`, avoiding stale drift
- **Custom features merged**: Hand-written features coexist with generated ones via `tckFeatures` aggregation
- **Separate traits**: Submodule sync and test support are independent concerns, composable via mill's mixin architecture
- **Top-level convenience**: `./mill tck.refresh` provides a single command for the common sync+generate workflow
