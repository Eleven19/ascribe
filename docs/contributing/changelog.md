# Maintaining the Changelog

This repository keeps a checked-in `CHANGELOG.md` and follows the
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) structure.

The changelog is curated. `git-cliff` supplements GitHub Release notes, but it
does not replace the checked-in changelog.

## Required structure

`CHANGELOG.md` must always contain:

- `## [Unreleased]`
- `### Added`
- `### Changed`
- `### Fixed`
- `### Documentation`
- `### CI`

Released versions use headings like:

```markdown
## [1.2.3] - 2026-03-14
```

Tags include the leading `v`, but changelog versions do not.

## What goes in each bucket

### Added

Use this for new user-visible or operator-visible capabilities.

Examples:

- new interceptor behavior
- new release packaging support
- new installation paths

### Changed

Use this for behavior changes, compatibility shifts, or notable refactors that
alter how the project works without being a pure fix.

Examples:

- changed release-note generation flow
- changed packaging behavior
- changed command mapping semantics

### Fixed

Use this for defects and regressions.

Examples:

- fixed parser edge cases
- fixed release asset naming bugs
- fixed workflow behavior

### Documentation

Use this for contributor docs, operator docs, and process docs.

Examples:

- added release guide updates
- documented changelog policy
- added diagrams for the release flow

### CI

Use this for automation, workflow, build-pipeline, and repository-governance
changes.

Examples:

- added or changed GitHub Actions workflows
- changed release automation
- updated CODEOWNERS or branch protection support docs

## Day-to-day flow

Normal change work should update `[Unreleased]` in the same PR whenever the
change is notable enough to appear in release notes.

```text
           +----------------------+
           | implement change     |
           +----------+-----------+
                      |
                      v
           +----------------------+
           | decide if change is  |
           | release-worthy       |
           +----------+-----------+
                      |
            no        | yes
      no changelog    v
          edit   +----------------------+
                 | add entry under      |
                 | [Unreleased] bucket  |
                 +----------+-----------+
                            |
                            v
                 +----------------------+
                 | merge PR             |
                 +----------------------+
```

## Preparing a release

Before cutting `v1.2.3`:

1. Move the relevant `[Unreleased]` entries into `## [1.2.3] - YYYY-MM-DD`.
2. Leave a fresh empty `[Unreleased]` section at the top.
3. Keep the standard buckets under `[Unreleased]`.
4. Verify the version matches the intended release tag without the leading `v`.

The release workflow fails if the exact version section is missing.

```text
          +-----------------------------+
          | prepare release version     |
          | e.g. 1.2.3                  |
          +-------------+---------------+
                        |
                        v
          +-----------------------------+
          | exact section exists in     |
          | CHANGELOG.md ?              |
          +-------------+---------------+
                        |
              no        | yes
         release fails  v
                 +----------------------+
                 | release workflow may |
                 | publish notes        |
                 +----------------------+
```

## Local verification

Useful local checks:

```bash
rg -n "^## \\[(Unreleased|1\\.2\\.3)\\]" CHANGELOG.md
scripts/ci/build-release-notes.sh 0.1.0
bash scripts/ci/test-build-release-notes.sh
```

If `scripts/ci/build-release-notes.sh <version>` fails, the release workflow
will fail for the same reason.
