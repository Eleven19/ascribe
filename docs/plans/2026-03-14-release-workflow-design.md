# Release Workflow Design

## Goal

Add a first-pass release workflow that produces platform-specific GraalVM native executables with Mill, packages them into `mise`-compatible archives, and publishes them to GitHub Releases.

## Scope

This design covers:

- Release artifact packaging for native images
- GitHub Actions workflow structure for build and publish
- Versioning and prerelease behavior for tag and manual releases
- Artifact naming and archive layout for `mise` GitHub backend compatibility

This design does not yet cover:

- Maven Central publication
- Signature and provenance publication
- Installer scripts or package manager integrations beyond GitHub Releases and `mise`

## Constraints

- The repository already uses Mill with `NativeImageModule` in [build.mill.yaml](/home/damian/code/repos/github/Eleven19/mill-interceptor/build.mill.yaml).
- Release builds should target the platforms Mill currently documents as supported for native executables:
  - Linux x64
  - Linux arm64
  - macOS x64
  - macOS arm64
  - Windows x64
- Release triggers must support both:
  - Pushing a version tag like `v1.2.3`
  - Manual `workflow_dispatch` with a semantic version input like `1.2.3` or `1.2.3-rc.1`
- Tags include a leading `v`, but internal version handling strips the `v`.

## Recommended Approach

Mill should own the release artifact contract, and GitHub Actions should own orchestration and publishing.

That means the build should expose explicit tasks that:

- Produce a native executable for a target platform
- Package the executable into the correct archive format
- Emit a deterministic asset name from version and target metadata

GitHub Actions should:

- Resolve the requested version and release metadata
- Run a matrix build across platforms
- Collect finished archives
- Create or update the GitHub Release
- Upload the archives and a checksum manifest

This keeps release behavior testable from the repo and avoids burying naming logic inside workflow shell scripts.

## Artifact Contract

Release assets should be archive-based rather than raw binaries.

- Unix-like targets should use `.tar.gz`
- Windows targets should use `.zip`

Recommended asset names:

- `mill-interceptor-v<version>-x86_64-unknown-linux-gnu.tar.gz`
- `mill-interceptor-v<version>-aarch64-unknown-linux-gnu.tar.gz`
- `mill-interceptor-v<version>-x86_64-apple-darwin.tar.gz`
- `mill-interceptor-v<version>-aarch64-apple-darwin.tar.gz`
- `mill-interceptor-v<version>-x86_64-pc-windows-msvc.zip`

Archive contents:

- Archive root contains the executable directly
- Executable name is `mill-interceptor` on Unix-like targets
- Executable name is `mill-interceptor.exe` on Windows

This layout is intentionally simple for `mise`’s GitHub backend, which infers install candidates from asset names and archive structure.

## Version and Release Semantics

For tag-triggered releases:

- Workflow runs on tags matching `v*`
- Version is derived from the tag by stripping the leading `v`
- Release is marked prerelease when the semantic version contains a prerelease suffix

For manual releases:

- `workflow_dispatch` takes a required version input without the leading `v`
- Workflow validates the version against semantic version rules
- Workflow creates or ensures the existence of tag `v<version>`
- Release is marked prerelease when the semantic version contains a prerelease suffix

This keeps tags, release objects, and downloadable assets aligned even for manual releases.

## Workflow Shape

The workflow should have three jobs.

### 1. Metadata

Responsibilities:

- Determine whether the run came from a tag push or manual dispatch
- Normalize version and tag values
- Validate semantic version input
- Compute prerelease state
- Expose normalized outputs for later jobs

Outputs:

- `version`
- `tag`
- `prerelease`
- `release_name`

### 2. Matrix Build

Responsibilities:

- Build release archives for each supported platform
- Use OS-specific GitHub-hosted runners that match the target
- Call Mill packaging tasks rather than hand-rolling archives in the workflow
- Upload produced archives as workflow artifacts

Initial matrix:

- `ubuntu-latest` or explicit Linux x64 runner for `x86_64-unknown-linux-gnu`
- `ubuntu-24.04-arm` for `aarch64-unknown-linux-gnu`
- Intel macOS runner for `x86_64-apple-darwin`
- ARM macOS runner for `aarch64-apple-darwin`
- `windows-latest` for `x86_64-pc-windows-msvc`

Each matrix entry should define:

- Runner label
- Canonical target identifier
- Archive extension
- Executable suffix

### 3. Publish

Responsibilities:

- Download all workflow artifacts
- Generate a checksum manifest
- Create or update the GitHub Release
- Mark prerelease versus stable from metadata outputs
- Upload archives and checksums to the release

Publishing should only proceed when every matrix leg succeeds.

## Mill Changes

The Mill build should gain release-specific tasks, likely in a small custom module or build task group.

Needed responsibilities:

- Map a target identifier to:
  - archive extension
  - executable suffix
  - final asset name
- Invoke native image generation through Mill’s native-image support
- Stage the built executable in a temporary directory
- Package it into the correct archive format
- Return the produced archive path as the build output

The first pass should prefer straightforward build-task composition over deep custom infrastructure. The key requirement is deterministic output names and repo-owned packaging logic.

## Testing Strategy

Testing should focus first on the packaging contract.

Add verification that:

- A given version and target produce the expected release asset filename
- Archive contents place the executable at the archive root
- Windows and Unix-like platforms get the expected extension and executable name

CI validation should then cover:

- Manual prerelease cut via `workflow_dispatch`
- Stable release cut via pushed `v*` tag

Full end-to-end GitHub Release assertions can start manual and lightweight in this first phase.

## Risks and Mitigations

### Cross-platform native image behavior

Risk:
Native image can fail differently across runners, especially for ARM and Windows.

Mitigation:
Keep each matrix leg isolated and publish only when all legs succeed.

### `mise` compatibility drift

Risk:
If asset names or archive structure are ambiguous, `mise` may not auto-detect correctly.

Mitigation:
Use canonical target triples in asset names and keep the executable at archive root.

### Manual release inconsistency

Risk:
Manual runs could create releases without matching tags.

Mitigation:
Have the workflow create or verify the matching `v<version>` tag before publishing.

## References

- [Mill installation and native executable support](https://mill-build.org/mill/cli/installation-ide.html)
- [mise GitHub backend](https://mise.jdx.dev/dev-tools/backends/github.html)
- [GitHub-hosted runners reference](https://docs.github.com/actions/reference/runners/github-hosted-runners)
