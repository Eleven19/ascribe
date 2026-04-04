# Scala.js Cross-Compilation Design

**Date:** 2026-04-04
**Status:** Approved
**Scope:** core, asg, bridge modules + Mill upgrade

## Motivation

Enable ascribe's parser and ASG to run in the browser, be consumed by Scala.js downstream projects, and support shared logic in full-stack Scala applications. The core parsing pipeline (core → asg → bridge) has dependencies that all support Scala.js, making cross-compilation feasible without code changes.

## Use Cases

1. **Browser-based AsciiDoc parsing** — embed the parser in a web app or WASM target
2. **Cross-platform artifacts** — downstream consumers on JS can depend on ascribe
3. **Full-stack shared logic** — server-side rendering + client-side preview

## Scope

### Modules to Cross-Compile (JVM + JS)

| Module | Artifact | External Dependencies | JS Support |
|--------|----------|----------------------|------------|
| `ascribe-core` | `ascribe-core` | parsley 4.6.2 | Yes |
| `ascribe-asg` | `ascribe-asg` | zio-blocks-schema 0.0.29 | Yes |
| `ascribe-bridge` | `ascribe-bridge` | (core + asg only) | Yes |

### Stays JVM-Only

- `ascribe-pipeline-core`, `ascribe-pipeline-html`, `ascribe-pipeline-markdown`
- `ascribe-pipeline-ox` (depends on ox + os-lib, JVM-only)
- `ascribe-pipeline-kyo` (depends on kyo, JVM-only)
- `ascribe-itest`, `ascribe-tck-runner` (Cucumber, JVM-only)

### Additional Changes

- Mill upgrade: 1.1.3 → 1.1.5
- Scala.js version: 1.18.2

## Architecture

### PlatformScalaModule Pattern

Each cross-compiled module uses Mill's `PlatformScalaModule` trait, which provides:

- `moduleDir` — points one level up so `jvm` and `js` submodules share the parent's source directory
- `sourcesFolders` — automatically includes `src/` (shared) + `src-{platform}/` (platform-specific)
- `artifactNameParts` — drops the platform segment so both publish under the same artifact name
- `platformCrossSuffix` — `""` for JVM, `"js"` for JS

### Module Structure

Each cross-compiled module follows this pattern:

```scala
// ascribe/core/package.mill
package build.ascribe.core

import mill.*, scalalib.*, scalajslib.*

trait CoreModule extends CommonScalaModule with PlatformScalaModule with PublishSupport {
  def mvnDeps = Seq(mvn"com.github.j-mie6::parsley::4.6.2")
}

object jvm extends CoreModule {
  object test extends ScalaTests with CommonScalaTestModule with TestModule.ZioTest {
    def mvnDeps = Seq(
      mvn"dev.zio::zio-test::2.1.24",
      mvn"dev.zio::zio-test-sbt::2.1.24"
    )
  }
}

object js extends CoreModule with ScalaJSModule {
  def scalaJSVersion = "1.18.2"
  object test extends ScalaJSTests with CommonScalaTestModule with TestModule.ZioTest {
    def mvnDeps = Seq(
      mvn"dev.zio::zio-test::2.1.24",
      mvn"dev.zio::zio-test-sbt::2.1.24"
    )
  }
}
```

### Source Directory Layout

No source splitting is needed initially — all code in core, asg, and bridge is platform-agnostic.

```
ascribe/core/
├── src/           ← shared code (both platforms, existing sources)
├── src-jvm/       ← JVM-only code (empty initially, available if needed)
├── src-js/        ← JS-only code (empty initially, available if needed)
├── test/src/      ← shared test code
├── test/src-jvm/  ← JVM-only tests (empty initially)
└── test/src-js/   ← JS-only tests (empty initially)
```

### Module Addressing

| Before | After (JVM) | After (JS) |
|--------|-------------|------------|
| `ascribe.core.compile` | `ascribe.core.jvm.compile` | `ascribe.core.js.compile` |
| `ascribe.core.test` | `ascribe.core.jvm.test` | `ascribe.core.js.test` |
| `ascribe.asg.compile` | `ascribe.asg.jvm.compile` | `ascribe.asg.js.compile` |
| `ascribe.bridge.compile` | `ascribe.bridge.jvm.compile` | `ascribe.bridge.js.compile` |

### Dependency Wiring

Cross-compiled modules wire platform-consistently:

```
bridge.jvm.moduleDeps  →  [core.jvm, asg.jvm]
bridge.js.moduleDeps   →  [core.js, asg.js]
```

JVM-only downstream modules reference the `.jvm` variant:

```
pipeline-ox.moduleDeps      →  [pipeline.core, core.jvm, asg.jvm, bridge.jvm]
pipeline-kyo.moduleDeps     →  [pipeline.core, core.jvm, asg.jvm, bridge.jvm]
tck-runner.moduleDeps       →  [core.jvm, asg.jvm, bridge.jvm]
itest.moduleDeps            →  [core.jvm, asg.jvm, bridge.jvm]
```

## Build Support Changes

### New Trait: CommonScalaJSModule

```scala
// mill-build/src/build/Modules.scala
trait CommonScalaTestModule extends ScalaModule with scalafmt.ScalafmtModule

trait CommonScalaJSModule extends ScalaJSModule with scalafmt.ScalafmtModule {
  def scalaJSVersion = "1.18.2"
}
```

### mill-build Plugin Dependency

The `mill-build/package.mill` (or equivalent) needs the Scala.js Mill plugin:

```scala
import mill.scalajslib.*
```

Mill 1.1.5 includes `scalajslib` as a built-in module, so no additional plugin dependency is needed.

### Mill Version Upgrade

`build.mill.yaml`:
```yaml
mill-version: 1.1.5
```

## Files to Change

### Convert YAML → Scala (required for PlatformScalaModule)

1. `ascribe/core/package.mill.yaml` → `ascribe/core/package.mill`
2. `ascribe/core/test/package.mill.yaml` → absorbed into `ascribe/core/package.mill`
3. `ascribe/asg/package.mill.yaml` → `ascribe/asg/package.mill`
4. `ascribe/asg/test/package.mill.yaml` → absorbed into `ascribe/asg/package.mill`
5. `ascribe/bridge/package.mill.yaml` → `ascribe/bridge/package.mill`
6. `ascribe/bridge/test/package.mill.yaml` → absorbed into `ascribe/bridge/package.mill`

### Update moduleDeps References

These modules reference core/asg/bridge and need `.jvm` suffix:

7. `ascribe/pipeline/core/package.mill.yaml` — `moduleDeps: core.jvm`
8. `ascribe/pipeline/html/package.mill.yaml` — `moduleDeps: pipeline.core, core.jvm`
9. `ascribe/pipeline/markdown/package.mill.yaml` — `moduleDeps: pipeline.core, core.jvm`
10. `ascribe/pipeline/ox/package.mill.yaml` — `moduleDeps: pipeline.core, core.jvm, asg.jvm, bridge.jvm`
11. `ascribe/pipeline/kyo/package.mill.yaml` — `moduleDeps: pipeline.core, core.jvm, asg.jvm, bridge.jvm`
12. `ascribe/itest/package.mill.yaml` — `moduleDeps: core.jvm, asg.jvm, bridge.jvm`
13. `ascribe/tck-runner/package.mill` — `moduleDeps: core.jvm, asg.jvm, bridge.jvm`
14. `ascribe/tck-runner/test/package.mill` — inherits from tck-runner

### Build Support

15. `build.mill.yaml` — bump mill-version to 1.1.5
16. `mill-build/src/build/Modules.scala` — add `CommonScalaJSModule` trait
17. `mill-build/src/build/DocSiteSupport.scala` — may need adjustment for `.jvm` module path

### CI

18. `.github/workflows/ci.yml` — add JS compilation and test steps

## Testing Strategy

### JVM Tests

Existing tests continue running under `ascribe.core.jvm.test`, `ascribe.asg.jvm.test`, etc. No changes to test code.

### JS Tests

JS test modules use ZIO Test — the same framework as JVM. Both `zio-test` and `zio-test-sbt` publish Scala.js artifacts (`zio-test_sjs1_3`, `zio-test-sbt_sjs1_3`), so `TestModule.ZioTest` mixed into `ScalaJSTests` resolves the correct platform artifacts automatically via Mill's `::` dependency resolution.

- Core JS tests: run existing parser tests under Scala.js to validate platform compatibility
- ASG JS tests: run codec tests under Scala.js
- Bridge JS tests: run conversion tests under Scala.js

Shared test sources live in `test/src/`. If any tests use JVM-specific features (e.g., `java.io`), they go in `test/src-jvm/` and a JS-compatible alternative in `test/src-js/`.

### CI Matrix

```yaml
- name: Build & Test (JVM)
  run: ./mill __.jvm.compile && ./mill __.jvm.test

- name: Build & Test (JS)
  run: ./mill ascribe.core.js.compile ascribe.asg.js.compile ascribe.bridge.js.compile
```

## Publishing

Cross-compiled modules publish two artifacts each:

| Module | JVM Artifact | JS Artifact |
|--------|-------------|-------------|
| core | `ascribe-core_3` | `ascribe-core_sjs1_3` |
| asg | `ascribe-asg_3` | `ascribe-asg_sjs1_3` |
| bridge | `ascribe-bridge_3` | `ascribe-bridge_sjs1_3` |

Mill handles the `_sjs1_` suffix automatically via `ScalaJSModule`.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| zio-blocks-schema JS artifact may have issues | Verify with `cellar deps` and a smoke compile early |
| ZIO Test on Scala.js runtime issues | `zio-test-sbt_sjs1_3:2.1.24` is published and contains `ZTestFramework`; smoke test early |
| Parser performance on JS | Not a blocker for publishing; optimize later if needed |
| Breaking change for downstream Mill commands | Document new `./mill ascribe.core.jvm.compile` addressing |
