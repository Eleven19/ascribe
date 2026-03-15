---
name: changelog-maintenance
description: Maintain the checked-in Keep a Changelog file for release-worthy changes.
---

# Changelog Maintenance

Use this skill whenever a task changes user-visible behavior, operator-visible
behavior, release automation, documentation, or CI in a way that should appear
in release notes.

## Required checks

1. Update `CHANGELOG.md` under `## [Unreleased]`
2. Use one of these buckets:
   - `Added`
   - `Changed`
   - `Fixed`
   - `Documentation`
   - `CI`
3. Keep the bucket wording concise and release-note ready
4. Verify the change against `docs/contributing/changelog.md`

## References

- `CHANGELOG.md`
- `docs/contributing/changelog.md`

## Verification

Run:

```bash
rg -n "^## \\[Unreleased\\]" CHANGELOG.md
```
