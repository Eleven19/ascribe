# Scala.js Cross-Compilation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cross-compile ascribe's core, asg, and bridge modules to Scala.js using Mill's PlatformScalaModule, enabling browser-based AsciiDoc parsing and cross-platform artifact publishing.

**Architecture:** Each cross-compiled module gets a shared trait extending `PlatformScalaModule`, with `object jvm` and `object js` submodules. Sources stay in `src/` (shared), with `src-jvm/` and `src-js/` available for platform-specific code. Downstream JVM-only modules reference the `.jvm` variant.

**Tech Stack:** Mill 1.1.5, Scala 3.8.2, Scala.js 1.18.2, PlatformScalaModule, ZIO Test on both platforms.

**Spec:** `docs/superpowers/specs/2026-04-04-scalajs-cross-compilation-design.md`

**Worktree:** `../ascribe-scalajs` on branch `feat/scalajs-cross-compilation`

---

## File Map

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `build.mill.yaml` | Bump Mill version to 1.1.5 |
| Modify | `mill-build/src/build/Modules.scala` | Add `CommonScalaJSModule` trait |
| Delete | `ascribe/core/package.mill.yaml` | Replaced by Scala package.mill |
| Create | `ascribe/core/package.mill` | Core module with jvm/js submodules |
| Delete | `ascribe/core/test/package.mill.yaml` | Absorbed into parent package.mill |
| Delete | `ascribe/asg/package.mill.yaml` | Replaced by Scala package.mill |
| Create | `ascribe/asg/package.mill` | ASG module with jvm/js submodules |
| Delete | `ascribe/asg/test/package.mill.yaml` | Absorbed into parent package.mill |
| Delete | `ascribe/bridge/package.mill.yaml` | Replaced by Scala package.mill |
| Create | `ascribe/bridge/package.mill` | Bridge module with jvm/js submodules |
| Delete | `ascribe/bridge/test/package.mill.yaml` | Absorbed into parent package.mill |
| Modify | `ascribe/package.mill.yaml` | Update moduleDeps to use .jvm |
| Modify | `ascribe/pipeline/core/package.mill.yaml` | Update moduleDeps to use core.jvm |
| Modify | `ascribe/pipeline/html/package.mill.yaml` | Update moduleDeps to use core.jvm |
| Modify | `ascribe/pipeline/markdown/package.mill.yaml` | Update moduleDeps to use core.jvm |
| Modify | `ascribe/pipeline/ox/package.mill.yaml` | Update moduleDeps to use .jvm variants |
| Modify | `ascribe/pipeline/kyo/package.mill.yaml` | Update moduleDeps to use .jvm variants |
| Modify | `ascribe/itest/package.mill.yaml` | Update moduleDeps to use .jvm variants |
| Modify | `ascribe/tck-runner/package.mill` | Update moduleDeps to use .jvm variants |
| Modify | `ascribe/tck-runner/test/package.mill` | Update parent reference |
| Modify | `.github/workflows/ci.yml` | Add JS build/test steps, update lint |

---

## Task 1: Upgrade Mill to 1.1.5

**Files:**
- Modify: `build.mill.yaml`

- [ ] **Step 1: Update Mill version**

In the worktree (`../ascribe-scalajs`), edit `build.mill.yaml`:

```yaml
mill-version: 1.1.5
mill-build:
  jvmVersion: "temurin:21"
  jvmIndexVersion: latest.release
```

- [ ] **Step 2: Verify Mill upgrade works**

Run:
```bash
cd ../ascribe-scalajs
./mill version
```
Expected: `1.1.5`

Then verify existing compilation still works:
```bash
./mill __.compile
```
Expected: All modules compile successfully.

- [ ] **Step 3: Run existing tests**

```bash
./mill __.test
```
Expected: All tests pass (same as before upgrade).

- [ ] **Step 4: Commit**

```bash
git add build.mill.yaml
git commit -m "chore: upgrade Mill from 1.1.3 to 1.1.5"
```

---

## Task 2: Add CommonScalaJSModule trait

**Files:**
- Modify: `mill-build/src/build/Modules.scala`

- [ ] **Step 1: Add the import and trait**

Add `import mill.scalajslib.*` at the top and the new trait at the bottom of `mill-build/src/build/Modules.scala`:

```scala
package build

import mill.*
import mill.scalalib.*
import mill.scalajslib.*

trait CommonScalaModule extends ScalaModule with scalafmt.ScalafmtModule {
  override def scalaVersion = Task {
    "3.8.2"
  }

  override def scalacOptions = Task {
    Seq(
      "-Wvalue-discard",
      "-Wnonunit-statement",
      "-Wconf:msg=(unused.*value|discarded.*value|pure.*statement):error",
      "-language:strictEquality",
      "-deprecation",
      "-feature",
      "-Werror"
    )
  }
}

trait CommonScalaTestModule extends ScalaModule

trait CommonScalaJSModule extends ScalaJSModule {
  def scalaJSVersion = "1.18.2"
}

/** Groups `ascribe/pipeline/{core,html,...}` so Mill discovers `ascribe.pipeline.*` children. */
trait PipelineContainerModule extends Module
```

- [ ] **Step 2: Verify compilation**

```bash
./mill __.compile
```
Expected: All modules compile. The new trait is available but not yet used.

- [ ] **Step 3: Commit**

```bash
git add mill-build/src/build/Modules.scala
git commit -m "build: add CommonScalaJSModule trait with Scala.js 1.18.2"
```

---

## Task 3: Convert core module to PlatformScalaModule

**Files:**
- Delete: `ascribe/core/package.mill.yaml`
- Delete: `ascribe/core/test/package.mill.yaml`
- Create: `ascribe/core/package.mill`

- [ ] **Step 1: Create the Scala package.mill**

Create `ascribe/core/package.mill`:

```scala
package build.ascribe.core

import mill.*, scalalib.*, scalajslib.*

trait CoreModule extends _root_.build.CommonScalaModule
    with PlatformScalaModule
    with _root_.build.PublishSupport
    with _root_.build.DocSiteSupport {

    override def artifactName = "ascribe-core"

    def mvnDeps = Seq(
        mvn"com.github.j-mie6::parsley::4.6.2"
    )
}

object jvm extends CoreModule {
    object test extends ScalaTests
        with _root_.build.CommonScalaTestModule
        with TestModule.ZioTest {

        def mvnDeps = Seq(
            mvn"dev.zio::zio-test::2.1.24",
            mvn"dev.zio::zio-test-sbt::2.1.24"
        )
    }
}

object js extends CoreModule with _root_.build.CommonScalaJSModule {
    object test extends ScalaJSTests
        with _root_.build.CommonScalaTestModule
        with TestModule.ZioTest {

        def mvnDeps = Seq(
            mvn"dev.zio::zio-test::2.1.24",
            mvn"dev.zio::zio-test-sbt::2.1.24"
        )
    }
}
```

- [ ] **Step 2: Delete old YAML configs**

```bash
rm ascribe/core/package.mill.yaml
rm ascribe/core/test/package.mill.yaml
```

- [ ] **Step 3: Compile JVM variant**

```bash
./mill ascribe.core.jvm.compile
```
Expected: Compiles successfully (same sources, different module path).

- [ ] **Step 4: Compile JS variant**

```bash
./mill ascribe.core.js.compile
```
Expected: Compiles successfully. This is the first Scala.js compilation — parsley resolves as `parsley_sjs1_3`.

- [ ] **Step 5: Run JVM tests**

```bash
./mill ascribe.core.jvm.test
```
Expected: All existing core tests pass.

- [ ] **Step 6: Run JS tests**

```bash
./mill ascribe.core.js.test
```
Expected: All core tests pass under Scala.js (Node.js runtime). If any tests fail due to JVM-specific APIs, they need to be moved to `test/src-jvm/`.

- [ ] **Step 7: Commit**

```bash
git add ascribe/core/package.mill
git rm ascribe/core/package.mill.yaml ascribe/core/test/package.mill.yaml
git commit -m "build(core): convert to PlatformScalaModule with JVM + JS targets"
```

---

## Task 4: Convert asg module to PlatformScalaModule

**Files:**
- Delete: `ascribe/asg/package.mill.yaml`
- Delete: `ascribe/asg/test/package.mill.yaml`
- Create: `ascribe/asg/package.mill`

- [ ] **Step 1: Create the Scala package.mill**

Create `ascribe/asg/package.mill`:

```scala
package build.ascribe.asg

import mill.*, scalalib.*, scalajslib.*

trait AsgModule extends _root_.build.CommonScalaModule
    with PlatformScalaModule
    with _root_.build.PublishSupport {

    def mvnDeps = Seq(
        mvn"dev.zio::zio-blocks-schema::0.0.29"
    )
}

object jvm extends AsgModule {
    object test extends ScalaTests
        with _root_.build.CommonScalaTestModule
        with TestModule.Junit4 {

        def mvnDeps = Seq(
            mvn"org.scalameta::munit::1.0.4"
        )
    }
}

object js extends AsgModule with _root_.build.CommonScalaJSModule {
    object test extends ScalaJSTests
        with _root_.build.CommonScalaTestModule
        with TestModule.Junit4 {

        def mvnDeps = Seq(
            mvn"org.scalameta::munit::1.0.4"
        )
    }
}
```

- [ ] **Step 2: Delete old YAML configs**

```bash
rm ascribe/asg/package.mill.yaml
rm ascribe/asg/test/package.mill.yaml
```

- [ ] **Step 3: Compile and test both variants**

```bash
./mill ascribe.asg.jvm.compile && ./mill ascribe.asg.js.compile
./mill ascribe.asg.jvm.test && ./mill ascribe.asg.js.test
```
Expected: Both compile and tests pass. This validates `zio-blocks-schema` works on Scala.js.

- [ ] **Step 4: Commit**

```bash
git add ascribe/asg/package.mill
git rm ascribe/asg/package.mill.yaml ascribe/asg/test/package.mill.yaml
git commit -m "build(asg): convert to PlatformScalaModule with JVM + JS targets"
```

---

## Task 5: Convert bridge module to PlatformScalaModule

**Files:**
- Delete: `ascribe/bridge/package.mill.yaml`
- Delete: `ascribe/bridge/test/package.mill.yaml`
- Create: `ascribe/bridge/package.mill`

- [ ] **Step 1: Create the Scala package.mill**

Create `ascribe/bridge/package.mill`:

```scala
package build.ascribe.bridge

import mill.*, scalalib.*, scalajslib.*

trait BridgeModule extends _root_.build.CommonScalaModule
    with PlatformScalaModule
    with _root_.build.PublishSupport

object jvm extends BridgeModule {
    def moduleDeps = Seq(build.ascribe.core.jvm, build.ascribe.asg.jvm)

    object test extends ScalaTests
        with _root_.build.CommonScalaTestModule
        with TestModule.Junit4 {

        def mvnDeps = Seq(
            mvn"org.scalameta::munit::1.0.4"
        )
    }
}

object js extends BridgeModule with _root_.build.CommonScalaJSModule {
    def moduleDeps = Seq(build.ascribe.core.js, build.ascribe.asg.js)

    object test extends ScalaJSTests
        with _root_.build.CommonScalaTestModule
        with TestModule.Junit4 {

        def mvnDeps = Seq(
            mvn"org.scalameta::munit::1.0.4"
        )
    }
}
```

- [ ] **Step 2: Delete old YAML configs**

```bash
rm ascribe/bridge/package.mill.yaml
rm ascribe/bridge/test/package.mill.yaml
```

- [ ] **Step 3: Compile and test both variants**

```bash
./mill ascribe.bridge.jvm.compile && ./mill ascribe.bridge.js.compile
./mill ascribe.bridge.jvm.test && ./mill ascribe.bridge.js.test
```
Expected: Both compile and tests pass.

- [ ] **Step 4: Commit**

```bash
git add ascribe/bridge/package.mill
git rm ascribe/bridge/package.mill.yaml ascribe/bridge/test/package.mill.yaml
git commit -m "build(bridge): convert to PlatformScalaModule with JVM + JS targets"
```

---

## Task 6: Update downstream JVM-only modules

All modules that depend on core, asg, or bridge must now reference the `.jvm` variant.

**Files:**
- Modify: `ascribe/package.mill.yaml`
- Modify: `ascribe/pipeline/core/package.mill.yaml`
- Modify: `ascribe/pipeline/html/package.mill.yaml`
- Modify: `ascribe/pipeline/markdown/package.mill.yaml`
- Modify: `ascribe/pipeline/ox/package.mill.yaml`
- Modify: `ascribe/pipeline/kyo/package.mill.yaml`
- Modify: `ascribe/itest/package.mill.yaml`
- Modify: `ascribe/tck-runner/package.mill`
- Modify: `ascribe/tck-runner/test/package.mill`

- [ ] **Step 1: Update ascribe/package.mill.yaml**

```yaml
extends: [_root_.build.CommonScalaModule, _root_.build.PublishSupport]
artifactName: ascribe
moduleDeps:
  - build.ascribe.core.jvm
  - build.ascribe.pipeline.core
  - build.ascribe.pipeline.html
  - build.ascribe.pipeline.markdown
  - build.ascribe.asg.jvm
  - build.ascribe.bridge.jvm
```

- [ ] **Step 2: Update ascribe/pipeline/core/package.mill.yaml**

```yaml
extends: [_root_.build.CommonScalaModule, _root_.build.PublishSupport]
artifactName: ascribe-pipeline-core
moduleDeps:
  - build.ascribe.core.jvm
mvnDeps: []
```

- [ ] **Step 3: Update ascribe/pipeline/html/package.mill.yaml**

```yaml
extends: [_root_.build.CommonScalaModule, _root_.build.PublishSupport]
artifactName: ascribe-pipeline-html
moduleDeps:
  - build.ascribe.pipeline.core
  - build.ascribe.core.jvm
mvnDeps:
  - com.lihaoyi::scalatags::0.13.1
```

- [ ] **Step 4: Update ascribe/pipeline/markdown/package.mill.yaml**

```yaml
extends: [_root_.build.CommonScalaModule, _root_.build.PublishSupport]
artifactName: ascribe-pipeline-markdown
moduleDeps:
  - build.ascribe.pipeline.core
  - build.ascribe.core.jvm
mvnDeps:
  - dev.zio::zio-blocks-docs::0.0.29
```

- [ ] **Step 5: Update ascribe/pipeline/ox/package.mill.yaml**

```yaml
extends: [_root_.build.CommonScalaModule, _root_.build.PublishSupport]
artifactName: ascribe-pipeline-ox
moduleDeps:
  - build.ascribe.pipeline.core
  - build.ascribe.core.jvm
  - build.ascribe.asg.jvm
  - build.ascribe.bridge.jvm
mvnDeps:
  - com.lihaoyi::os-lib::0.11.8
  - com.softwaremill.ox::core::0.3.0
```

- [ ] **Step 6: Update ascribe/pipeline/kyo/package.mill.yaml**

```yaml
extends: [_root_.build.CommonScalaModule, _root_.build.PublishSupport]
artifactName: ascribe-pipeline-kyo
moduleDeps:
  - build.ascribe.pipeline.core
  - build.ascribe.core.jvm
  - build.ascribe.asg.jvm
  - build.ascribe.bridge.jvm
mvnDeps:
  - io.getkyo::kyo-core::1.0-RC1
```

- [ ] **Step 7: Update ascribe/itest/package.mill.yaml**

```yaml
extends: [_root_.build.CommonScalaModule, _root_.build.CommonScalaTestModule, TestModule.Junit5]
moduleDeps:
  - build.ascribe.core.jvm
  - build.ascribe.asg.jvm
  - build.ascribe.bridge.jvm
mvnDeps:
  - org.scalameta::munit::1.0.4
  - io.cucumber::cucumber-scala::8.25.1
  - io.cucumber:cucumber-junit-platform-engine:7.20.1
  - org.junit.platform:junit-platform-suite:1.11.4
  - org.junit.platform:junit-platform-suite-engine:1.11.4
  - org.junit.jupiter:junit-jupiter-engine:5.11.4
```

- [ ] **Step 8: Update ascribe/tck-runner/package.mill**

```scala
package build.ascribe.`tck-runner`

import mill.*
import mill.scalalib.*

object `package` extends _root_.build.CommonScalaModule with _root_.build.TckSubmoduleSupport {

  def moduleDeps = Seq(build.ascribe.core.jvm, build.ascribe.asg.jvm, build.ascribe.bridge.jvm)

  def tckRefresh() = Task.Command {
    val syncResult = tckSync()()
    Task.log.info(s"TCK submodule synced: ${syncResult.path}")
    val genResult = _root_.build_.ascribe.`tck-runner`.test.package_.generateTckFeatures()
    Task.log.info(s"TCK features generated: ${genResult.path}")
    genResult
  }
}
```

- [ ] **Step 9: Update ascribe/tck-runner/test/package.mill**

The test module extends from the parent's `ScalaTests`. Since the parent is now `build.ascribe.\`tck-runner\`` (unchanged package), this should still work. Verify:

```scala
package build.ascribe.`tck-runner`.test

import mill.*
import mill.scalalib.*
import mill.scalalib.TestModule

object `package` extends build.ascribe.`tck-runner`.ScalaTests with _root_.build.CommonScalaTestModule with TestModule.Junit5 with _root_.build.TckTestSupport {

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
    mill.api.BuildCtx.withFilesystemCheckerDisabled {
      val featuresDir = moduleDir / "resources" / "features"
      if (os.exists(featuresDir)) Seq(PathRef(featuresDir)) else Seq.empty
    }
  }
}
```

No changes needed to this file — the parent hasn't changed its package path.

- [ ] **Step 10: Verify full compilation**

```bash
./mill __.compile
```
Expected: All modules compile with the new `.jvm` references.

- [ ] **Step 11: Run all tests**

```bash
./mill __.test
```
Expected: All tests pass (JVM + JS).

- [ ] **Step 12: Commit**

```bash
git add ascribe/package.mill.yaml \
  ascribe/pipeline/core/package.mill.yaml \
  ascribe/pipeline/html/package.mill.yaml \
  ascribe/pipeline/markdown/package.mill.yaml \
  ascribe/pipeline/ox/package.mill.yaml \
  ascribe/pipeline/kyo/package.mill.yaml \
  ascribe/itest/package.mill.yaml \
  ascribe/tck-runner/package.mill
git commit -m "build: update downstream modules to reference .jvm variants"
```

---

## Task 7: Update CI workflow

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Update CI to include JS build and update lint paths**

```yaml
name: CI

on:
  push:
    branches: [main]
    tags-ignore:
      - "v*"
  pull_request:
    branches: [main]

# Cancel in-progress runs for the same branch/PR
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  lint:
    name: Lint
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Check formatting
        run: ./mill ascribe.core.jvm.checkFormat

  build:
    name: Build & Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: true

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - name: Compile all modules
        run: ./mill __.compile

      - name: Run all tests
        run: ./mill __.test

  build-js:
    name: Build & Test (Scala.js)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - uses: actions/setup-node@v4
        with:
          node-version: "22"

      - name: Compile JS modules
        run: ./mill ascribe.core.js.compile ascribe.asg.js.compile ascribe.bridge.js.compile

      - name: Run JS tests
        run: ./mill ascribe.core.js.test ascribe.asg.js.test ascribe.bridge.js.test
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add Scala.js build and test job"
```

---

## Task 8: Final validation and push

- [ ] **Step 1: Full clean build**

```bash
./mill clean && ./mill __.compile
```
Expected: All modules compile from scratch.

- [ ] **Step 2: Full test suite**

```bash
./mill __.test
```
Expected: All tests pass on both JVM and JS.

- [ ] **Step 3: Verify JS-specific commands work**

```bash
./mill ascribe.core.js.fastLinkJS
./mill ascribe.asg.js.fastLinkJS
./mill ascribe.bridge.js.fastLinkJS
```
Expected: JS linking succeeds, producing `.js` output files.

- [ ] **Step 4: Push and create PR**

```bash
git push -u origin feat/scalajs-cross-compilation
```

Create PR with:
```bash
gh pr create --title "feat: add Scala.js cross-compilation for core, asg, bridge" --body "..."
```
