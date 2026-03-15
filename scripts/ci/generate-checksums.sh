#!/usr/bin/env bash
set -euo pipefail

artifacts_dir="${1:?artifacts directory is required}"

cd "$artifacts_dir"
sha256sum * > SHA256SUMS
