#!/usr/bin/env bash
set -euo pipefail

release_tag="${RELEASE_TAG:?RELEASE_TAG is required}"
artifacts_dir="${1:?artifacts directory is required}"

gh release upload "$release_tag" "$artifacts_dir"/* --clobber
