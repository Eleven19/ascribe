---
name: release-process
description: Prepare and validate releases that publish curated changelog content plus generated git-cliff notes.
---

# Release Process

Use this skill for release workflow work, release docs, changelog promotion from
`[Unreleased]`, or changes that affect GitHub Release publication.

## Required checks

1. Ensure the target version exists in `CHANGELOG.md` as:
   - `## [1.2.3] - YYYY-MM-DD`
2. Keep `## [Unreleased]` present above the released section
3. Verify release-note generation locally before claiming success
4. Follow the release flow in `docs/contributing/releasing.md`

## References

- `docs/contributing/releasing.md`
- `docs/contributing/changelog.md`
- `CHANGELOG.md`
- `cliff.toml`
- `scripts/ci/build-release-notes.sh`

## Verification

Run:

```bash
scripts/ci/build-release-notes.sh 0.1.0
bash scripts/ci/test-build-release-notes.sh
```
