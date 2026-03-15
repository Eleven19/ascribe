#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

cat >"$tmpdir/CHANGELOG.md" <<'EOF'
# Changelog

## [Unreleased]

### Added

- Placeholder unreleased entry.

## [1.2.3] - 2026-03-14

### Added

- Added release-note generation coverage.

### Changed

- Changed the release process to publish curated notes first.
EOF

release_notes_file="$tmpdir/release-notes.md"

(
    cd "$repo_root"
    CHANGELOG_PATH="$tmpdir/CHANGELOG.md" \
    RELEASE_NOTES_FILE="$release_notes_file" \
    scripts/ci/build-release-notes.sh 1.2.3
)

test -f "$release_notes_file"

first_added_line="$(rg -n "^- Added release-note generation coverage\\.$" "$release_notes_file" -m 1 | cut -d: -f1)"
generated_heading_line="$(rg -n "^## Generated Notes$" "$release_notes_file" -m 1 | cut -d: -f1)"

test -n "$first_added_line"
test -n "$generated_heading_line"
test "$first_added_line" -lt "$generated_heading_line"

if (
    cd "$repo_root"
    CHANGELOG_PATH="$tmpdir/CHANGELOG.md" \
    RELEASE_NOTES_FILE="$tmpdir/missing.md" \
    scripts/ci/build-release-notes.sh 9.9.9
); then
    echo "expected missing version to fail" >&2
    exit 1
fi
