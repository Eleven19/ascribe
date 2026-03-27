# Releasing

This document covers two publishing pipelines:

1. **Maven Central** — library JARs published to Maven Central via Sonatype
2. **Native Archives** — GraalVM native executables published to GitHub Releases

---

## Maven Central Publishing

The project publishes Scala library artifacts to Maven Central under the group `io.eleven19.ascribe`.

### Coordinates

Published modules include (Scala 3 suffix `_3`):

- `io.eleven19.ascribe:ascribe-core_3` — parser, AST, CST
- `io.eleven19.ascribe:ascribe_3` — umbrella (convenience aggregate; optional)
- `io.eleven19.ascribe:ascribe-pipeline-core_3`, `ascribe-pipeline-html_3`, `ascribe-pipeline-markdown_3`, `ascribe-pipeline-kyo_3`, `ascribe-pipeline-ox_3`
- `io.eleven19.ascribe:ascribe-asg_3`, `ascribe-bridge_3`

JARs are consumed from Maven Central. GitHub Releases carry release notes only (no Mill `out.jar` fat JAR).

### How it works

Publishing uses Mill's built-in `SonatypeCentralPublishModule`. The `PublishSupport` trait in `mill-build/src/build/PublishSupport.scala` configures POM metadata, and the `scripts/ci/publish-central.sh` script maps repo secrets to the `MILL_*` env vars that Mill reads automatically:

- `MILL_SONATYPE_USERNAME` / `MILL_SONATYPE_PASSWORD` — Sonatype Central credentials
- `MILL_PGP_PASSPHRASE` / `MILL_PGP_SECRET_BASE64` — GPG signing key

### Release publish (stable)

Triggered by pushing a `v*` tag or via manual `workflow_dispatch`:

```bash
git tag v1.2.3
git push origin v1.2.3
```

The `publish-central.yml` workflow extracts the version from the tag (stripping the `v` prefix) and publishes to Maven Central's staging repository.

You can also trigger it manually from GitHub Actions and provide a version like `1.2.3`.

### SNAPSHOT publish

Triggered via manual `workflow_dispatch` on the `publish-snapshot.yml` workflow. It reads the base version from the `VERSION` file and appends `-SNAPSHOT` (or `-<branch>-SNAPSHOT` for non-main branches).

```bash
# From the GitHub Actions UI:
# Run workflow → publish-snapshot.yml → main branch
```

### Required secrets

The following GitHub Actions secrets must be configured:

| Secret | Description |
|--------|-------------|
| `ELEVEN19_SONATYPE_USERNAME` | Sonatype Central username |
| `ELEVEN19_SONATYPE_PASSWORD` | Sonatype Central password |
| `ELEVEN19_IO_PGP_SECRET_BASE64` | Base64-encoded GPG private key |
| `ELEVEN19_IO_PGP_PASSPHRASE` | GPG key passphrase |

### Local testing

```bash
# Check the publish version computation
bash scripts/ci/compute-publish-version.sh "$(cat VERSION)"

# Dry-run publish locally (requires credentials)
PUBLISH_VERSION=0.1.0-SNAPSHOT ./mill __.publishSonatypeCentral
```

### VERSION file

The `VERSION` file at the repo root contains the current base version (e.g. `0.1.0`). This is used by the SNAPSHOT workflow and as the default fallback in `PublishSupport`.

---

## Native Archives

This section covers GraalVM native executables published through GitHub Releases as platform-specific archives that are compatible with `mise`'s GitHub backend.

### Overview

The native release pipeline has three goals:

1. Build a native executable for each supported target with Mill and GraalVM native-image.
2. Package each executable into a predictable archive layout that `mise` can install from GitHub Releases.
3. Publish all archives plus `SHA256SUMS` to a single GitHub release driven by either a pushed tag or a manual workflow dispatch.
4. Publish release notes that start with curated changelog content and then append generated `git-cliff` notes.

The workflow definition lives in `.github/workflows/release.yml`. The workflow shell logic is intentionally kept in `scripts/ci/` so the YAML stays declarative and the release steps can be tested locally.

### Release flow diagram

```text
             +----------------------+
             |  tag push: v1.2.3    |
             |         or           |
             | workflow_dispatch    |
             | version=1.2.3-rc.1   |
             +----------+-----------+
                        |
                        v
             +----------------------+
             | metadata job         |
             | - normalize version  |
             | - compute tag        |
             | - mark prerelease    |
             +----------+-----------+
                        |
                        v
             +----------------------+
             | build matrix job     |
             | per target:          |
             | - resolve asset name |
             | - build native image |
             | - package archive    |
             | - upload artifact    |
             +----------+-----------+
                        |
                        v
             +----------------------+
             | publish job          |
             | - create tag if      |
             |   workflow_dispatch  |
             | - validate changelog |
             | - build release body |
             | - download artifacts |
             | - generate checksums |
             | - create/update      |
             |   GitHub release     |
             | - upload assets      |
             +----------------------+
```

### Supported targets

The release workflow currently targets:

- `x86_64-unknown-linux-gnu`
- `aarch64-unknown-linux-gnu`
- `x86_64-apple-darwin`
- `aarch64-apple-darwin`
- `x86_64-pc-windows-msvc`

These match the native executable platforms Mill currently documents as supported.

### Release entrypoints

Two release entrypoints are supported:

- Push a tag like `v1.2.3` or `v1.2.3-rc.1`
- Run `workflow_dispatch` and provide `version` as `1.2.3` or `1.2.3-rc.1`

Tag pushes are the normal stable-release path. `workflow_dispatch` is useful for prereleases, dry-run-style cuts against a branch head, or when you want the workflow to create the matching Git tag for you.

### Versioning rules

The repository uses two related but different version forms:

- Git tags always include a leading `v`
- Internal workflow version values do not include the leading `v`

Examples:

- Tag: `v1.2.3`
- Version input: `1.2.3`
- Tag: `v1.2.3-rc.1`
- Version input: `1.2.3-rc.1`

The release metadata action normalizes the version by stripping any leading `v`, rebuilding the tag as `v<version>`, and detecting prereleases from semantic version syntax.

### Workflow structure

The release workflow has three jobs.

#### 1. `metadata`

This job:

- Reads the version from either the pushed tag or `workflow_dispatch`
- Validates semantic version syntax
- Computes:
  - normalized `version`
  - `tag`
  - `prerelease`
  - `release_name`

The normalization logic lives in `.github/actions/release-metadata/`.

```text
input version/tag
      |
      v
+--------------------+
| strip leading `v`  |
+--------------------+
      |
      v
+--------------------+
| validate semver    |
+--------------------+
      |
      v
+--------------------+      +----------------------+
| prerelease suffix? |----->| prerelease=true      |
+--------------------+ yes  +----------------------+
      |
      no
      v
+----------------------+
| prerelease=false     |
+----------------------+
      |
      v
+----------------------+
| emit version/tag     |
+----------------------+
```

#### 2. `build`

This job runs as a matrix over the supported targets. For each target it:

- Resolves the final archive name through Mill
- Builds the native image and packages it into a release archive
- Uploads the resulting archive as a workflow artifact

The build job delegates shell logic to:

- `scripts/ci/compute-archive-name.sh`
- `scripts/ci/build-release-archive.sh`

#### 3. `publish`

This job only runs after all matrix builds succeed. It:

- Creates the tag for `workflow_dispatch` releases if it does not already exist
- Installs `git-cliff`
- Validates that `CHANGELOG.md` contains the exact version section
- Builds a release body with curated changelog content first and generated notes second
- Downloads the matrix artifacts
- Generates `SHA256SUMS`
- Creates or updates the GitHub release
- Uploads the platform archives and checksums

The publish job delegates shell logic to:

- `scripts/ci/create-release-tag.sh`
- `scripts/ci/install-git-cliff.sh`
- `scripts/ci/build-release-notes.sh`
- `scripts/ci/generate-checksums.sh`
- `scripts/ci/create-or-update-github-release.sh`
- `scripts/ci/upload-release-assets.sh`

```text
workflow_dispatch? ---- yes ----> create missing tag v<version>
        | no
        v
validate exact CHANGELOG section
        |
        v
build hybrid release body
        |
        v
download build artifacts
        |
        v
generate SHA256SUMS
        |
        v
release exists? ---- yes ----> update release metadata
        | no
        v
create release
        |
        v
upload archives + SHA256SUMS
```

### Changelog and release notes contract

The checked-in `CHANGELOG.md` is the curated source of truth for release
summaries.

For a release like `v1.2.3`, the workflow requires an exact changelog section:

```markdown
## [1.2.3] - 2026-03-14
```

If that section does not exist, the publish job fails before it creates or
updates the GitHub Release.

The final GitHub Release body is assembled as:

1. Curated changelog section for the exact version
2. Divider
3. Generated `git-cliff` notes for the same repository state

```text
         +----------------------------+
         | CHANGELOG.md exact        |
         | version section           |
         +-------------+--------------+
                       |
                       v
         +----------------------------+
         | scripts/ci/build-         |
         | release-notes.sh          |
         +------+------+-------------+
                |      |
                |      v
                |  +------------------+
                |  | git-cliff notes  |
                |  +------------------+
                |      |
                +------+
                       |
                       v
         +----------------------------+
         | final GitHub Release body  |
         +----------------------------+
```

### Artifact contract

Asset names follow the pattern:

- `mill-interceptor-v<version>-<target>.tar.gz` for Unix-like targets
- `mill-interceptor-v<version>-<target>.zip` for Windows

Examples:

- `mill-interceptor-v1.2.3-x86_64-unknown-linux-gnu.tar.gz`
- `mill-interceptor-v1.2.3-aarch64-apple-darwin.tar.gz`
- `mill-interceptor-v1.2.3-x86_64-pc-windows-msvc.zip`

Archive contents place the executable at the archive root:

- `mill-interceptor` on Unix-like targets
- `mill-interceptor.exe` on Windows

That layout keeps extraction simple for `mise`.

### Mill release commands

The build exposes release-specific commands through `mill-build/src/build/ReleaseSupport.scala`.

Useful local commands:

```bash
./mill show releaseTargets
./mill show releaseAssetName --version 1.2.3 --target x86_64-unknown-linux-gnu
./mill show releaseArchive --version 1.2.3 --target x86_64-unknown-linux-gnu
```

These commands are what the workflow uses under the hood. If you need to debug packaging behavior, start here rather than editing the workflow first.

### Packaging flow diagram

```text
releaseArchive(version, target)
          |
          v
+---------------------------+
| validate target triple    |
+---------------------------+
          |
          v
+---------------------------+
| run Mill nativeImage      |
+---------------------------+
          |
          v
+---------------------------+
| stage executable as       |
| mill-interceptor(.exe)    |
+---------------------------+
          |
          v
   +------+------+
   | target OS?  |
   +------+------+
          |
   +------+------+
   |             |
 unix          windows
   |             |
   v             v
tar.gz         zip
   |             |
   +------+------+
          |
          v
+---------------------------+
| emit final release asset  |
+---------------------------+
```

### Local verification

Before pushing release-workflow changes, run at least:

```bash
COURSIER_CACHE=/tmp/coursier ./mill test.testLocal
COURSIER_CACHE=/tmp/coursier ./mill show releaseTargets
COURSIER_CACHE=/tmp/coursier ./mill show releaseArchive --version 1.2.3 --target aarch64-unknown-linux-gnu
scripts/verify-release-archive.sh \
  out/releaseArchive.dest/mill-interceptor-v1.2.3-aarch64-unknown-linux-gnu.tar.gz \
  mill-interceptor-v1.2.3-aarch64-unknown-linux-gnu.tar.gz \
  mill-interceptor
```

For workflow-script verification, useful spot checks are:

```bash
RUNNER_TEMP=/tmp scripts/ci/compute-archive-name.sh 1.2.3 aarch64-unknown-linux-gnu
RUNNER_TEMP=/tmp scripts/ci/build-release-archive.sh 1.2.3 aarch64-unknown-linux-gnu mill-interceptor-v1.2.3-aarch64-unknown-linux-gnu.tar.gz
RUNNER_TEMP=/tmp scripts/ci/install-git-cliff.sh 2.12.0
scripts/ci/build-release-notes.sh 0.1.0
bash scripts/ci/test-build-release-notes.sh
```

### Manual release flow

To cut a manual prerelease or stable release:

1. Open the `Release` workflow in GitHub Actions.
2. Run `workflow_dispatch`.
3. Enter `version` without the leading `v`.

Examples:

- `1.2.3`
- `1.2.3-rc.1`

The workflow will:

1. Normalize the version and prerelease state.
2. Ensure the corresponding tag is `v<version>`.
3. Validate the exact changelog section for that version.
4. Build all supported platform archives.
5. Generate `SHA256SUMS`.
6. Build the hybrid release notes body.
7. Create or update the GitHub release.
8. Upload the archives and checksums.

### Tag-driven stable release flow

To cut a stable release from a tag:

```bash
git tag v1.2.3
git push origin v1.2.3
```

That path skips manual tag creation because the pushed tag is already the source of truth for the release.

### Missing changelog section

If a release fails with a missing-version changelog error, fix `CHANGELOG.md`
before retrying. The workflow intentionally refuses to fall back to generated
notes only.

### Checksums

The publish job generates `SHA256SUMS` from the downloaded platform archives and uploads it to the GitHub release alongside the binaries.

To verify a downloaded archive locally:

```bash
sha256sum -c SHA256SUMS --ignore-missing
```

### `mise` expectations

The current release contract is designed for `mise`'s GitHub backend:

- asset names encode version and target
- archive types are conventional for each platform
- the executable is at archive root

If you change the asset naming pattern or move the executable into nested directories, verify `mise` installation behavior before shipping the change.

### Troubleshooting

#### Native image builds fail on one matrix target

Start by checking whether the failure is target-specific or shared:

- If only one runner fails, inspect that matrix job’s native-image output first.
- If every target fails in the same place, inspect the Mill command or metadata logic instead.

#### Workflow dispatch created the wrong release type

Check the `metadata` job output and confirm the version follows semantic versioning:

- `1.2.3` -> stable release
- `1.2.3-rc.1` -> prerelease

#### Archive naming looks wrong

Verify locally with:

```bash
./mill show releaseAssetName --version 1.2.3 --target x86_64-pc-windows-msvc
```

#### Workflow YAML is getting hard to read

Put shell logic in `scripts/ci/` and keep the workflow file focused on orchestration.
