#!/usr/bin/env bash
set -euo pipefail

version="${1:?version is required}"
target="${2:?target is required}"
output_file="${GITHUB_OUTPUT:-}"

archive_name="$(
  COURSIER_CACHE="${RUNNER_TEMP:-/tmp}/coursier" ./mill show releaseAssetName \
    --version "$version" \
    --target "$target" \
  | sed -n 's/^"//; s/"$//; p' \
  | tail -n 1
)"

if [[ -z "$archive_name" ]]; then
  echo "Unable to determine archive name" >&2
  exit 1
fi

if [[ -n "$output_file" ]]; then
  printf 'archive_name=%s\n' "$archive_name" >>"$output_file"
fi

printf '%s\n' "$archive_name"
