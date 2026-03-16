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
  /** Path to the TCK git submodule. Defaults to submodules/tck relative to workspace root. */
  def tckSubmodulePath: T[os.Path] = Task {
    Task.workspace / "submodules" / "tck"
  }

  /** Initialize/update the TCK submodule. Always runs when invoked (Command, not cached Task). */
  def tckSync(): Command[PathRef] = Task.Command {
    val submodulePath = tckSubmodulePath()
    os.proc("git", "submodule", "update", "--init", submodulePath)
      .call(cwd = Task.workspace)
    PathRef(submodulePath)
  }
}
```

- `tckSubmodulePath` has a sensible default (`submodules/tck`), overridable via YAML or Scala
- `tckSync` is a `Task.Command` — always runs when explicitly invoked, never cached. This is intentional: submodule sync is an imperative action ("go fetch now"), not a derivable build artifact.

### Trait 2: `TckTestSupport`

**File:** `mill-build/src/build/TckTestSupport.scala`

Generates Cucumber feature files from TCK test data and merges with custom features.

```scala
trait TckTestSupport extends JavaModule {
  /** Path to TCK test data root (containing tests/block/, tests/inline/).
    * Defaults to submodules/tck relative to workspace root.
    * This reads directly from the filesystem — does NOT depend on tckSync.
    * The submodule must be initialized beforehand (via tck.refresh or git submodule update).
    */
  def tckTestDataPath: T[os.Path] = Task {
    Task.workspace / "submodules" / "tck"
  }

  /** Hand-written feature files to merge with generated ones. Override to provide. */
  def customTckFeatures: T[Seq[PathRef]] = Task { Seq.empty[PathRef] }

  /** Generate .feature files from TCK test data.
    * Uses Task.workspace-relative paths in generated files (not findRepoRoot).
    */
  def generateTckFeatures: T[PathRef] = Task {
    val tckRoot = tckTestDataPath()
    val tckDir = tckRoot / "tests"
    val outDir = Task.dest / "features"
    os.makeDir.all(outDir)
    // Scan block/ and inline/ subdirectories for *-input.adoc files
    // Generate corresponding .feature files with Scenario Outline format
    // Use tckRoot.relativeTo(Task.workspace) for workspace-relative paths in feature examples
    // (logic ported from current GenerateTckFeatures.scala, replacing findRepoRoot with Task.workspace)
    PathRef(Task.dest)
  }

  /** Merged features: generated + custom. All entries are directories. */
  def tckFeatures: T[Seq[PathRef]] = Task {
    Seq(generateTckFeatures()) ++ customTckFeatures()
  }

  /** Override resources to include generated and custom TCK features alongside source resources. */
  override def resources: T[Seq[PathRef]] = Task {
    super.resources() ++ tckFeatures()
  }
}
```

- `tckTestDataPath` defaults to `submodules/tck`, overridable. **No Mill-level dependency on `tckSync`** — reads the filesystem directly. This is deliberate: running `./mill ascribe.tck-runner.test` should not trigger a git fetch.
- `customTckFeatures` defaults to empty, override to include hand-written scenarios
- `generateTckFeatures` writes into `Task.dest`, not the source tree. Uses `Task.workspace` instead of the old `findRepoRoot` hack for path resolution.
- `tckFeatures` merges generated and custom features (all entries are directories)
- `resources` is overridden to include `tckFeatures` output — requires extending `JavaModule`

### Top-Level Command: `tck.mill`

**File:** `tck.mill` (root-level, next to `build.mill.yaml`)

Orchestrates sync + generation as a single convenience command:

```scala
import mill._

object tck extends Module {
  /** Sync the TCK submodule and regenerate feature files.
    * Runs tckSync (on tck-runner) then generateTckFeatures (on tck-runner.test).
    */
  def refresh() = Task.Command {
    ascribe.`tck-runner`.tckSync()()
    ascribe.`tck-runner`.test.generateTckFeatures()
  }
}
```

Invoked as: `./mill tck.refresh`

**Fallback:** If Mill 1.1.3 does not support auxiliary `.mill` files alongside a YAML root build, this command moves to a `tckRefresh` command on the `tck-runner` module itself, invoked as `./mill ascribe.tck-runner.tckRefresh`.

## Data Flow

```
./mill tck.refresh (Task.Command — always runs)
    1. tckSync (TckSubmoduleSupport, Task.Command)
    |   runs: git submodule update --init submodules/tck
    |   side effect: populates submodules/tck/ on disk
    |
    2. generateTckFeatures (TckTestSupport, cached Task)
        reads: tckTestDataPath (defaults to submodules/tck on disk)
        scans: {tckTestDataPath}/tests/block/*/  and  tests/inline/*/
        generates: .feature files into Task.dest
        returns: PathRef to Task.dest

./mill ascribe.tck-runner.test (does NOT trigger tckSync)
    +-- resources includes:
        +-- tckFeatures (TckTestSupport)
        |   +-- generateTckFeatures output (reads submodules/tck, must be initialized)
        |   +-- customTckFeatures (hand-written, from test/resources/features/)
        +-- junit-platform.properties (stays in source tree)
```

**Intentional decoupling:** `generateTckFeatures` and `tckSync` are not connected via Mill task dependencies. `tckSync` is an imperative command; `generateTckFeatures` reads from the filesystem. Running tests does not trigger a git fetch. Developers run `./mill tck.refresh` explicitly when they want to update.

### Path Resolution in Tests

Generated feature files contain workspace-relative paths for TCK input/output files (e.g., `submodules/tck/tests/block/document/body-only-input.adoc`). `TckSteps.scala` must resolve these paths relative to the workspace root, not the working directory. The generation logic uses `Task.workspace` as the base (replacing the old `findRepoRoot` method).

## Module Wiring

- `ascribe/tck-runner/package.mill.yaml` — adds `_root_.build.TckSubmoduleSupport` to extends. Default `tckSubmodulePath` works without Scala configuration.
- `ascribe/tck-runner/test/package.mill` — **converted from YAML to Scala** because it needs to:
  - Mix in `TckTestSupport` (which overrides `resources`)
  - Override `customTckFeatures` to point at hand-written feature files in `test/resources/features/`

### Test Module Skeleton (`ascribe/tck-runner/test/package.mill`)

```scala
package build.ascribe.`tck-runner`

import mill._
import mill.scalalib._
import mill.scalalib.TestModule

object test extends ScalaTests with TestModule.Junit5 with TckTestSupport {
  def mvnDeps = Seq(
    mvn"org.scalameta::munit::1.0.4",
    mvn"io.cucumber::cucumber-scala::8.25.1",
    mvn"io.cucumber:cucumber-junit-platform-engine:7.20.1",
    mvn"org.junit.platform:junit-platform-suite:1.11.4",
    mvn"org.junit.platform:junit-platform-suite-engine:1.11.4",
    mvn"org.junit.jupiter:junit-jupiter-engine:5.11.4",
    mvn"dev.zio::zio-json::0.7.3"
  )

  override def customTckFeatures: T[Seq[PathRef]] = Task {
    val featuresDir = millSourcePath / "resources" / "features"
    if (os.exists(featuresDir)) Seq(PathRef(featuresDir)) else Seq.empty
  }
}
```

Only the test submodule requires Scala; the parent `tck-runner` module stays YAML.

## Cleanup

### Removed

- `ascribe/tck-runner/src/build/ascribe/tckrunner/GenerateTckFeatures.scala` — logic moves into `TckTestSupport`
- `ascribe/tck-runner/test/resources/features/*.feature` (7 generated files) — generated into `Task.dest` at build time
- `ascribe/tck-runner/test/package.mill.yaml` — replaced by `package.mill` (Scala)
- `os-lib` dependency on `tck-runner` main module (only needed for `GenerateTckFeatures`; verify no other source files use it before removing)

### Retained

- `ascribe/tck-runner/test/src/` — `TckSteps.scala` and Cucumber suite runner (test code)
- `ascribe/tck-runner/test/resources/junit-platform.properties` — Cucumber config
- `ascribe/tck-runner/test/resources/features/` — repurposed for hand-written custom features (initially empty with `.gitkeep`)

## Key Decisions

- **Convention-based defaults**: `tckSubmodulePath` and `tckTestDataPath` default to `submodules/tck`, overridable in YAML or Scala. No abstract members — traits work out of the box when mixed in via YAML.
- **`tckSync` is a Command, not a cached Task**: Submodule sync is imperative ("go fetch now"), not a derivable artifact. It always runs when invoked.
- **No Mill dependency between sync and generation**: `generateTckFeatures` reads from the filesystem, not from `tckSync` output. Running tests does not trigger git fetches. Developers explicitly run `./mill tck.refresh` to update.
- **Test module uses Scala config**: `tck-runner/test` converts from `package.mill.yaml` to `package.mill` because it needs to override `resources` and `customTckFeatures` with Scala logic.
- **Path resolution uses `Task.workspace`**: Generation logic replaces the old `findRepoRoot` hack with `Task.workspace` for reliable workspace-relative paths.
- **Generated files not checked in**: Feature files are build artifacts in `Task.dest`, avoiding stale drift.
- **Custom features merged**: Hand-written features coexist with generated ones via `tckFeatures` aggregation.
- **Separate traits**: Submodule sync and test support are independent concerns, composable via mill's mixin architecture.
- **Top-level convenience**: `./mill tck.refresh` provides a single command for the common sync+generate workflow, with a fallback if auxiliary `.mill` files aren't supported alongside YAML root builds.
- **`TckTestSupport` extends `JavaModule`**: Needed so the trait can override `resources`. This is appropriate since test modules already extend `JavaModule` via `ScalaModule`.
