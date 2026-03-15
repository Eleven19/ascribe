# Release Workflow Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build and publish `mise`-compatible GraalVM native release archives for all Mill-supported native-image targets via GitHub Releases.

**Architecture:** Move the root build from config-only Mill YAML to a programmable `build.mill` so the repository can own target metadata, archive naming, and packaging tasks. Use a GitHub Actions workflow with a metadata job, a platform matrix build job, and a publish job that attaches archives and checksums to GitHub Releases for both `v*` tag pushes and manual dispatch.

**Tech Stack:** Mill 1.1.x, Scala 3, GraalVM native-image, GitHub Actions, `gh`/GitHub release APIs, tar/zip packaging, `mise` GitHub backend conventions

---

### Task 1: Convert the Build to Programmable Mill Configuration

**Files:**
- Create: `build.mill`
- Modify: `build.mill.yaml`
- Test: `./mill show nativeImage`

**Step 1: Write the failing integration check**

Run:

```bash
./mill show releaseTargets
```

Expected: FAIL because the build does not yet expose any release-specific task.

**Step 2: Create a programmable root build while preserving current behavior**

Add a root module in `build.mill` that reproduces the current settings from `build.mill.yaml`:

```scala
//| mill-version: 1.1.3
//| mill-jvm-version: graalvm-java25:25.0.1
//| mill-build:
//|   jvmVersion: graalvm-java25:25.0.2
//|   jvmIndexVersion: latest.release

package build

import mill.*
import mill.scalalib.*

object `package` extends ScalaModule, NativeImageModule {
  def scalaVersion = "3.8.2"
  def jvmVersion = "graalvm-java25:25.0.2"
  def jvmIndexVersion = "latest.release"
  def mvnDeps = Seq(
    mvn"com.github.alexarchambault::case-app::2.1.0",
    mvn"com.outr::scribe::3.18.0",
    mvn"io.getkyo::kyo-core::1.0-RC1",
    mvn"io.getkyo::kyo-direct:1.0-RC1",
    mvn"io.getkyo::kyo-prelude:1.0-RC1"
  )
  def scalacOptions = Seq(
    "-Wvalue-discard",
    "-Wnonunit-statement",
    "-Wconf:msg=(unused.*value|discarded.*value|pure.*statement):error",
    "-language:strictEquality"
  )
  def mainClass = Some("io.github.eleven19.mill.interceptor.Main")
  def nativeImageOptions = Seq("--no-fallback")
}
```

Keep `build.mill.yaml` only if it is still required for config that cannot move into the build header; otherwise remove its duplicated module body so there is a single source of truth.

**Step 3: Add a minimal release metadata surface**

Extend `build.mill` with a release-target model and a simple observable task:

```scala
sealed trait ReleaseTarget {
  def id: String
  def archiveExt: String
  def executableName: String
}

def releaseTargets = Task {
  Seq(
    "x86_64-unknown-linux-gnu",
    "aarch64-unknown-linux-gnu",
    "x86_64-apple-darwin",
    "aarch64-apple-darwin",
    "x86_64-pc-windows-msvc"
  )
}
```

**Step 4: Run the check to verify the new task exists**

Run:

```bash
./mill show releaseTargets
```

Expected: PASS and prints the five canonical target identifiers.

**Step 5: Verify existing baseline behavior still works**

Run:

```bash
./mill show nativeImage
./mill test.test
```

Expected: the build still resolves native-image output and existing tests stay green.

**Step 6: Commit**

```bash
git add build.mill build.mill.yaml
git commit -m "build: convert root build to programmable mill"
```

### Task 2: Add Repo-Owned Release Naming and Packaging Tasks

**Files:**
- Modify: `build.mill`
- Create: `scripts/verify-release-archive.sh`
- Test: `scripts/verify-release-archive.sh`

**Step 1: Write the failing verification script**

Create `scripts/verify-release-archive.sh` that expects:

- an asset file path
- an expected file name
- an expected executable path inside the archive

Start with checks like:

```bash
#!/usr/bin/env bash
set -euo pipefail

archive_path="$1"
expected_name="$2"
expected_entry="$3"

test "$(basename "$archive_path")" = "$expected_name"
```

Run it against a placeholder or nonexistent build output so it fails.

Expected: FAIL because the build does not yet produce the named archive.

**Step 2: Add deterministic asset naming in `build.mill`**

Implement helpers in `build.mill` such as:

```scala
case class ReleaseArtifact(
  version: String,
  target: String,
  executableName: String,
  archiveExt: String
) {
  def assetName =
    s"mill-interceptor-v$version-$target.$archiveExt"
}
```

Add a release packaging command per target that:

- calls the native image task
- copies the produced executable into `Task.dest`
- names it `mill-interceptor` or `mill-interceptor.exe`
- archives it as `.tar.gz` or `.zip`
- returns a `PathRef` to the final archive

Preferred task shape:

```scala
def releaseArchive(version: String): Command[PathRef] = T.command {
  // target-specific packaging
}
```

If per-target cross modules are cleaner, use:

```scala
object release extends Cross[ReleaseTargetModule](...)
```

and expose:

```scala
release("x86_64-unknown-linux-gnu").releaseArchive("1.2.3")
```

**Step 3: Make the verification script assert archive layout**

For `.tar.gz`:

```bash
tar -tzf "$archive_path" | grep -Fx "$expected_entry"
```

For `.zip`:

```bash
unzip -Z1 "$archive_path" | grep -Fx "$expected_entry"
```

Require the executable to be at archive root.

**Step 4: Run the verification against one Unix target**

Run:

```bash
./mill show 'release["x86_64-unknown-linux-gnu"].releaseArchive("1.2.3")'
scripts/verify-release-archive.sh \
  out/release/x86_64-unknown-linux-gnu/releaseArchive.dest/mill-interceptor-v1.2.3-x86_64-unknown-linux-gnu.tar.gz \
  mill-interceptor-v1.2.3-x86_64-unknown-linux-gnu.tar.gz \
  mill-interceptor
```

Expected: PASS for filename and archive contents.

**Step 5: Run the verification against the Windows target shape**

Run:

```bash
./mill show 'release["x86_64-pc-windows-msvc"].releaseArchive("1.2.3")'
scripts/verify-release-archive.sh \
  out/release/x86_64-pc-windows-msvc/releaseArchive.dest/mill-interceptor-v1.2.3-x86_64-pc-windows-msvc.zip \
  mill-interceptor-v1.2.3-x86_64-pc-windows-msvc.zip \
  mill-interceptor.exe
```

Expected: PASS for zip layout and `.exe` naming.

**Step 6: Commit**

```bash
git add build.mill scripts/verify-release-archive.sh
git commit -m "build: add release archive packaging tasks"
```

### Task 3: Add Release Metadata Resolution and Version Validation

**Files:**
- Create: `.github/actions/release-metadata/action.yml`
- Create: `.github/actions/release-metadata/release-metadata.sh`
- Test: `.github/actions/release-metadata/release-metadata.sh`

**Step 1: Write the failing metadata check**

Create a shell-driven test invocation:

```bash
GITHUB_EVENT_NAME=workflow_dispatch \
INPUT_VERSION=1.2.3-rc.1 \
.github/actions/release-metadata/release-metadata.sh
```

Expected: FAIL because the action does not exist yet.

**Step 2: Implement metadata normalization**

In `.github/actions/release-metadata/release-metadata.sh`, add logic to:

- read either `GITHUB_REF_NAME` or dispatch `version`
- strip a leading `v` if present
- validate `MAJOR.MINOR.PATCH` with optional prerelease/build suffix
- derive:
  - `version`
  - `tag=v<version>`
  - `prerelease=true|false`
  - `release_name=<tag>`

Core logic:

```bash
version="${INPUT_VERSION:-${GITHUB_REF_NAME#v}}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([-.][0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?$ ]]
tag="v$version"
case "$version" in
  *-*) prerelease=true ;;
  *) prerelease=false ;;
esac
```

Write outputs via `$GITHUB_OUTPUT`.

**Step 3: Wrap it in a composite action**

Add `.github/actions/release-metadata/action.yml` so the workflow can call the script and read named outputs.

**Step 4: Verify stable and prerelease cases**

Run:

```bash
GITHUB_EVENT_NAME=workflow_dispatch INPUT_VERSION=1.2.3 .github/actions/release-metadata/release-metadata.sh
GITHUB_EVENT_NAME=workflow_dispatch INPUT_VERSION=1.2.3-rc.1 .github/actions/release-metadata/release-metadata.sh
```

Expected:

- stable version emits `prerelease=false`
- prerelease version emits `prerelease=true`

**Step 5: Verify invalid semver is rejected**

Run:

```bash
GITHUB_EVENT_NAME=workflow_dispatch INPUT_VERSION=1.2 .github/actions/release-metadata/release-metadata.sh
```

Expected: FAIL with a clear invalid-version message.

**Step 6: Commit**

```bash
git add .github/actions/release-metadata/action.yml .github/actions/release-metadata/release-metadata.sh
git commit -m "ci: add release metadata action"
```

### Task 4: Add the Matrix Release Workflow and GitHub Release Publishing

**Files:**
- Create: `.github/workflows/release.yml`
- Modify: `build.mill`
- Test: `.github/workflows/release.yml`

**Step 1: Write the failing workflow lint check**

Run:

```bash
git diff --exit-code -- .github/workflows/release.yml
test -f .github/workflows/release.yml
```

Expected: FAIL because the workflow file does not exist yet.

**Step 2: Create the release workflow**

Add `.github/workflows/release.yml` with:

- triggers:
  - `push.tags: ["v*"]`
  - `workflow_dispatch.inputs.version`
- permissions:
  - `contents: write`
- jobs:
  - `metadata`
  - `build`
  - `publish`

Use a matrix like:

```yaml
matrix:
  include:
    - target: x86_64-unknown-linux-gnu
      runner: ubuntu-latest
    - target: aarch64-unknown-linux-gnu
      runner: ubuntu-24.04-arm
    - target: x86_64-apple-darwin
      runner: macos-13
    - target: aarch64-apple-darwin
      runner: macos-14
    - target: x86_64-pc-windows-msvc
      runner: windows-latest
```

Build steps should:

- check out the repo
- set up Java/Graal environment as needed
- run the Mill release packaging task for the matrix target and resolved version
- upload the produced archive as a workflow artifact

Publish steps should:

- download all workflow artifacts
- generate `SHA256SUMS`
- create or update the release
- mark prerelease from metadata output
- upload all archives plus checksums

Use `gh release create` / `gh release upload --clobber` or the official GitHub release action, but keep release replacement idempotent.

**Step 3: Add tag creation for manual dispatch**

In the publish job, before creating the release, create the `v<version>` tag if it does not already exist:

```bash
git tag "$TAG" "$GITHUB_SHA"
git push origin "$TAG"
```

Guard this so tag-triggered runs skip the creation step.

**Step 4: Validate workflow structure locally**

Run:

```bash
sed -n '1,260p' .github/workflows/release.yml
```

Expected: confirms all three jobs, five matrix targets, and both triggers are present.

**Step 5: Manual end-to-end verification in GitHub**

Run a `workflow_dispatch` for `1.2.3-rc.1`.

Expected:

- tag `v1.2.3-rc.1` exists
- release `v1.2.3-rc.1` exists
- release is marked prerelease
- all platform archives and `SHA256SUMS` are attached

**Step 6: Commit**

```bash
git add .github/workflows/release.yml build.mill
git commit -m "ci: add native release workflow"
```

### Task 5: Document Release Usage and `mise` Artifact Expectations

**Files:**
- Modify: `README.md`
- Create: `docs/contributing/releasing.md`
- Modify: `docs/contributing/README.md`
- Test: `README.md`

**Step 1: Write the failing docs check**

Run:

```bash
rg -n "release|mise" README.md docs/contributing
```

Expected: FAIL to find any release process documentation for native archives.

**Step 2: Document the release workflow for maintainers**

In `docs/contributing/releasing.md`, explain:

- supported targets
- tag format versus version input format
- stable versus prerelease behavior
- how release archives are named
- how to manually trigger a release
- where checksums are published

**Step 3: Link the new release doc**

Update `docs/contributing/README.md` to include a release-process entry, and add a short user-facing note in `README.md` that native binaries are published via GitHub Releases in `mise`-compatible naming.

**Step 4: Verify docs mention the expected contract**

Run:

```bash
rg -n "v<version>|workflow_dispatch|mise|x86_64-unknown-linux-gnu|SHA256SUMS" README.md docs/contributing
```

Expected: PASS with hits in the new release documentation.

**Step 5: Commit**

```bash
git add README.md docs/contributing/README.md docs/contributing/releasing.md
git commit -m "docs: add release workflow guidance"
```

### Task 6: Final Verification Before Merge

**Files:**
- Test: `build.mill`
- Test: `.github/workflows/release.yml`
- Test: `scripts/verify-release-archive.sh`

**Step 1: Run the local verification suite**

Run:

```bash
./mill test.test
./mill show releaseTargets
./mill show 'release["x86_64-unknown-linux-gnu"].releaseArchive("1.2.3")'
scripts/verify-release-archive.sh \
  out/release/x86_64-unknown-linux-gnu/releaseArchive.dest/mill-interceptor-v1.2.3-x86_64-unknown-linux-gnu.tar.gz \
  mill-interceptor-v1.2.3-x86_64-unknown-linux-gnu.tar.gz \
  mill-interceptor
```

Expected: all local checks pass.

**Step 2: Run the GitHub prerelease smoke test**

Trigger the workflow manually for:

```text
1.2.3-rc.1
```

Expected:

- release is prerelease
- tag starts with `v`
- uploaded assets match the naming contract

**Step 3: Run the stable release smoke test**

Push:

```bash
git tag v1.2.3
git push origin v1.2.3
```

Expected:

- release is not prerelease
- same asset matrix is published
- checksums file matches uploaded archives

**Step 4: Request review**

```bash
git status --short
```

Expected: clean working tree before review or PR creation.
