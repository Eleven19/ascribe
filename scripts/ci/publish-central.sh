#!/usr/bin/env bash
set -euo pipefail

version="${1:?version is required}"

sonatype_username="${SONATYPE_USERNAME:?SONATYPE_USERNAME is required}"
sonatype_password="${SONATYPE_PASSWORD:?SONATYPE_PASSWORD is required}"
gpg_passphrase="${SONATYPE_GPG_PASSPHRASE:?SONATYPE_GPG_PASSPHRASE is required}"

export ASCRIBE_PUBLISH_VERSION="$version"

args=(
  ./mill
  --no-server
  ascribe.publish
  --sonatypeCreds
  "${sonatype_username}:${sonatype_password}"
  --signed
  true
  --release
  true
  --gpgArgs
  --batch
  --gpgArgs
  --yes
  --gpgArgs
  --pinentry-mode
  --gpgArgs
  loopback
  --gpgArgs
  --passphrase
  --gpgArgs
  "$gpg_passphrase"
)

"${args[@]}"
