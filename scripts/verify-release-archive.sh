#!/usr/bin/env bash
set -euo pipefail

archive_path="${1:?archive path is required}"
expected_name="${2:?expected name is required}"
expected_entry="${3:?expected entry is required}"

if [[ ! -f "$archive_path" ]]; then
  echo "Archive missing: $archive_path" >&2
  exit 1
fi

if [[ "$(basename "$archive_path")" != "$expected_name" ]]; then
  echo "Archive name mismatch: expected $expected_name got $(basename "$archive_path")" >&2
  exit 1
fi

case "$archive_path" in
  *.tar.gz)
    tar -tzf "$archive_path" | grep -Fx "$expected_entry" >/dev/null
    ;;
  *.zip)
    unzip -Z1 "$archive_path" | grep -Fx "$expected_entry" >/dev/null
    ;;
  *)
    echo "Unsupported archive type: $archive_path" >&2
    exit 1
    ;;
esac
