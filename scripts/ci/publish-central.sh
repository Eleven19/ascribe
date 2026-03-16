#!/usr/bin/env bash
set -euo pipefail

version="${1:?version is required}"

sonatype_username="${SONATYPE_USERNAME:?SONATYPE_USERNAME is required}"
sonatype_password="${SONATYPE_PASSWORD:?SONATYPE_PASSWORD is required}"
gpg_passphrase="${PGP_PASSPHRASE:?PGP_PASSPHRASE is required}"

export ASCRIBE_PUBLISH_VERSION="$version"

./mill --no-server ascribe.publish \
  --sonatypeCreds "${sonatype_username}:${sonatype_password}" \
  --signed true \
  --release true \
  --gpgArgs "--batch,--yes,--pinentry-mode,loopback,--passphrase,${gpg_passphrase}"
