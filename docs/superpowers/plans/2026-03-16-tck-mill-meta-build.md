# TCK Mill Meta-Build Tasks Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move TCK submodule sync and feature file generation from tck-runner runtime code into composable mill meta-build traits, with a top-level `tck.refresh` convenience command.

**Architecture:** Two meta-build traits (`TckSubmoduleSupport` for git submodule sync, `TckTestSupport` for feature generation and resource merging) plus a root-level `tck.mill` for the orchestrating command. The tck-runner test module converts from YAML to Scala config to wire resources.

**Tech Stack:** Mill 1.1.3 (YAML + Scala), Scala 3.8.2, os-lib (via Mill API), Cucumber/JUnit5

**Spec:** `docs/superpowers/specs/2026-03-16-tck-mill-meta-build-design.md`

---

## Chunk 1: Meta-Build Traits

### Task 1: Create `TckSubmoduleSupport` trait

**Files:**
- Create: `mill-build/src/build/TckSubmoduleSupport.scala`

- [ ] **Step 1: Create the trait file**

Reference `mill-build/src/build/PublishSupport.scala` for the existing pattern. Create:

```scala
package build

import mill.*

trait TckSubmoduleSupport extends Module {

  /** Path to the TCK git submodule. Defaults to submodules/tck relative to workspace root. */
  def tckSubmodulePath: T[os.Path] = Task {
    Task.workspace / "submodules" / "tck"
  }

  /** Initialize/update the TCK submodule. Always runs when invoked (Command, not cached). */
  def tckSync(): Command[PathRef] = Task.Command {
    val submodulePath = tckSubmodulePath()
    val workspace = Task.workspace
    Task.log.info(s"Syncing TCK submodule at $submodulePath")
    os.proc("git", "submodule", "update", "--init", submodulePath.relativeTo(workspace).toString)
      .call(cwd = workspace)
    Task.log.info("TCK submodule sync complete")
    PathRef(submodulePath)
  }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mill mill.build.compile`
Expected: Successful compilation (no errors)

- [ ] **Step 3: Commit**

```bash
git add mill-build/src/build/TckSubmoduleSupport.scala
git commit -m "feat: add TckSubmoduleSupport meta-build trait"
```

### Task 2: Create `TckTestSupport` trait

**Files:**
- Create: `mill-build/src/build/TckTestSupport.scala`

- [ ] **Step 1: Create the trait file**

Port the generation logic from `ascribe/tck-runner/src/build/ascribe/tckrunner/GenerateTckFeatures.scala` into a Mill task. Replace `findRepoRoot` with `Task.workspace`. Replace `pwd` with `Task.workspace`. Write output to `Task.dest` instead of the source tree.

```scala
package build

import mill.*
import mill.scalalib.JavaModule

trait TckTestSupport extends JavaModule {

  /** Path to TCK test data root (containing tests/block/, tests/inline/).
    * Defaults to submodules/tck relative to workspace root.
    * Reads directly from filesystem — does NOT depend on tckSync.
    * The submodule must be initialized beforehand (via tck.refresh or git submodule update).
    */
  def tckTestDataPath: T[os.Path] = Task {
    Task.workspace / "submodules" / "tck"
  }

  /** Hand-written feature files to merge with generated ones. Override to provide. */
  def customTckFeatures: T[Seq[PathRef]] = Task { Seq.empty[PathRef] }

  /** Generate .feature files from TCK test data.
    * Note: Directory names are title-cased per segment (e.g., "no-markup" -> "No-Markup").
    * Entries are sorted for deterministic output.
    */
  def generateTckFeatures: T[PathRef] = Task {
    val tckRoot = tckTestDataPath()
    val tckDir = tckRoot / "tests"
    val outDir = Task.dest / "features"
    os.makeDir.all(outDir)

    def generateForCategory(categoryDir: os.Path, prefix: String): Unit = {
      if (os.exists(categoryDir)) {
        for {
          constructDir <- os.list(categoryDir).sorted
          if os.isDir(constructDir)
        } {
          val constructName = constructDir.last.split("-").map(_.capitalize).mkString("-")
          val featureFile = outDir / s"$prefix${constructName}.feature"

          val featureLines = Seq(
            s"Feature: $prefix - $constructName",
            s"  Scenario Outline: TCK validation for <test_name>",
            s"""    Given the AsciiDoc input from "<input_file>"""",
            s"    When the input is parsed",
            s"""    Then the resulting ASG should match the expected JSON in "<output_file>"""",
            "",
            "    Examples:",
            "      | test_name | input_file | output_file |"
          )

          val workspace = Task.workspace
          val examples = os.list(constructDir).sorted
            .filter(_.last.endsWith("-input.adoc"))
            .map { inputFile =>
              val baseName = inputFile.last.stripSuffix("-input.adoc")
              val relativeInput = inputFile.relativeTo(workspace)
              val outputFile = constructDir / s"${baseName}-output.json"
              val relativeOutput = outputFile.relativeTo(workspace)
              s"      | $baseName | $relativeInput | $relativeOutput |"
            }

          if (examples.nonEmpty) {
            Task.log.info(s"  Generating feature: ${featureFile.last}")
            os.write.over(featureFile, (featureLines ++ examples).mkString("\n") + "\n")
          }
        }
      }
    }

    generateForCategory(tckDir / "block", "Block")
    generateForCategory(tckDir / "inline", "Inline")

    Task.log.info(s"Feature generation complete. Files written to: $outDir")
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

- [ ] **Step 2: Verify it compiles**

Run: `./mill mill.build.compile`
Expected: Successful compilation (no errors)

- [ ] **Step 3: Commit**

```bash
git add mill-build/src/build/TckTestSupport.scala
git commit -m "feat: add TckTestSupport meta-build trait"
```

## Chunk 2: Module Wiring

### Task 3: Wire `TckSubmoduleSupport` into tck-runner module

**Files:**
- Modify: `ascribe/tck-runner/package.mill.yaml`

- [ ] **Step 1: Add TckSubmoduleSupport to extends and remove os-lib**

The `os-lib` dependency was only needed for `GenerateTckFeatures.scala` which is moving to the meta-build. Verified: the only source file in `tck-runner/src/` is `GenerateTckFeatures.scala` (no other files use os-lib). It will be removed in Task 6, but we can remove the dependency now.

Update `ascribe/tck-runner/package.mill.yaml` to:

```yaml
extends: [ScalaModule, scalafmt.ScalafmtModule, _root_.build.TckSubmoduleSupport]

scalaVersion: "3.8.2"

moduleDeps:
  - ascribe
```

- [ ] **Step 2: Verify it compiles**

Run: `./mill __.compile`
Expected: Successful compilation

- [ ] **Step 3: Commit**

```bash
git add ascribe/tck-runner/package.mill.yaml
git commit -m "feat: wire TckSubmoduleSupport into tck-runner module"
```

### Task 4: Convert tck-runner test module from YAML to Scala

**Files:**
- Remove: `ascribe/tck-runner/test/package.mill.yaml`
- Create: `ascribe/tck-runner/test/package.mill`

- [ ] **Step 1: Create the Scala package file**

This replaces `ascribe/tck-runner/test/package.mill.yaml`. It mixes in `TckTestSupport`, overrides `customTckFeatures` to pick up hand-written features from `test/resources/features/`, and preserves all existing YAML dependencies.

```scala
package build.ascribe.`tck-runner`

import mill.*
import mill.scalalib.*
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

- [ ] **Step 2: Remove the old YAML config**

```bash
rm ascribe/tck-runner/test/package.mill.yaml
```

- [ ] **Step 3: Verify it compiles**

Run: `./mill __.compile`
Expected: Successful compilation. The test module should now have `TckTestSupport` mixed in, with `resources` automatically including generated features.

- [ ] **Step 4: Commit**

```bash
git add ascribe/tck-runner/test/package.mill
git rm ascribe/tck-runner/test/package.mill.yaml
git commit -m "feat: convert tck-runner test to Scala config with TckTestSupport"
```

## Chunk 3: Top-Level Command and Cleanup

### Task 5: Create `tck.mill` top-level command

**Files:**
- Create: `tck.mill`

- [ ] **Step 1: Create the root-level mill file**

```scala
package build

import mill.*

object tck extends Module {

  /** Sync the TCK submodule and regenerate feature files.
    * Runs tckSync (on tck-runner) then generateTckFeatures (on tck-runner.test).
    */
  def refresh() = Task.Command {
    val syncResult = ascribe.`tck-runner`.tckSync()()
    Task.log.info(s"TCK submodule synced: ${syncResult.path}")
    val genResult = ascribe.`tck-runner`.test.generateTckFeatures()
    Task.log.info(s"TCK features generated: ${genResult.path}")
    genResult
  }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mill __.compile`
Expected: Successful compilation

- [ ] **Step 3: Test the command resolves**

Run: `./mill resolve tck.refresh`
Expected: Shows `tck.refresh` in the output

If this fails (Mill 1.1.3 may not support auxiliary `.mill` files alongside YAML root), fall back to adding a `tckRefresh` command on the `tck-runner` module. In that case, create `ascribe/tck-runner/package.mill` instead (converting from YAML) and add:

```scala
def tckRefresh() = Task.Command {
  val syncResult = tckSync()()
  Task.log.info(s"TCK submodule synced: ${syncResult.path}")
  val genResult = test.generateTckFeatures()
  Task.log.info(s"TCK features generated: ${genResult.path}")
  genResult
}
```

- [ ] **Step 4: Commit**

```bash
git add tck.mill
git commit -m "feat: add tck.refresh top-level command"
```

### Task 6: Remove old GenerateTckFeatures and generated feature files

**Files:**
- Remove: `ascribe/tck-runner/src/build/ascribe/tckrunner/GenerateTckFeatures.scala`
- Remove: `ascribe/tck-runner/test/resources/features/BlockDocument.feature`
- Remove: `ascribe/tck-runner/test/resources/features/BlockHeader.feature`
- Remove: `ascribe/tck-runner/test/resources/features/BlockListing.feature`
- Remove: `ascribe/tck-runner/test/resources/features/BlockParagraph.feature`
- Remove: `ascribe/tck-runner/test/resources/features/BlockSection.feature`
- Remove: `ascribe/tck-runner/test/resources/features/BlockSidebar.feature`
- Remove: `ascribe/tck-runner/test/resources/features/InlineNo-markup.feature`
- Create: `ascribe/tck-runner/test/resources/features/.gitkeep`

- [ ] **Step 1: Remove the old generator**

```bash
rm ascribe/tck-runner/src/build/ascribe/tckrunner/GenerateTckFeatures.scala
```

- [ ] **Step 2: Remove generated feature files and add .gitkeep**

```bash
rm ascribe/tck-runner/test/resources/features/*.feature
touch ascribe/tck-runner/test/resources/features/.gitkeep
```

- [ ] **Step 3: Verify it compiles**

Run: `./mill __.compile`
Expected: Successful compilation. The `tck-runner` src directory may now be empty (only had `GenerateTckFeatures.scala`). This is fine — the module still serves as the parent for the test submodule and provides `TckSubmoduleSupport` tasks.

- [ ] **Step 4: Commit**

```bash
git rm ascribe/tck-runner/src/build/ascribe/tckrunner/GenerateTckFeatures.scala
git rm ascribe/tck-runner/test/resources/features/*.feature
git add ascribe/tck-runner/test/resources/features/.gitkeep
git commit -m "chore: remove old GenerateTckFeatures and generated feature files

Feature files are now generated into Task.dest by TckTestSupport trait.
The features/ directory is preserved for hand-written custom TCK scenarios."
```

**Note:** `TckSteps.scala` was reviewed and requires no changes for this task. Its `resolvePath` method walks up parent directories to find workspace-relative paths, which continues to work with the generated feature files. Connecting the actual parser is tracked separately (ascribe-lnn).

## Chunk 4: Verification

### Task 7: End-to-end verification

- [ ] **Step 1: Initialize the TCK submodule**

Run: `./mill ascribe.tck-runner.tckSync`
Expected: Git submodule initialized, `submodules/tck/` populated with TCK test data

- [ ] **Step 2: Test feature generation**

Run: `./mill ascribe.tck-runner.test.generateTckFeatures`
Expected: Feature files generated into Mill's `out/` directory. Check output logs for "Generating feature:" messages.

- [ ] **Step 3: Test the top-level refresh command**

Run: `./mill tck.refresh`
Expected: Both sync and generation run. Output shows sync followed by generation.

- [ ] **Step 4: Verify test resources include generated features**

Run: `./mill show ascribe.tck-runner.test.resources`
Expected: Output includes paths to both the source `resources/` directory (with `junit-platform.properties` and `features/` for custom features) and the generated `Task.dest` directory (with generated `.feature` files).

- [ ] **Step 5: Verify full compilation**

Run: `./mill __.compile`
Expected: All modules compile successfully.

- [ ] **Step 6: Commit submodule initialization if needed**

If `submodules/tck` was initialized during testing:
```bash
git add submodules/tck
git commit -m "chore: initialize TCK git submodule"
```
