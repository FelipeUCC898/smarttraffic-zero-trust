#!/bin/bash
set -euo pipefail
# Run only on an authorized VM, after secure delivery and review of the bundle.
zone=$1
case "$zone" in auth|data|ingest|core|app) ;; *) echo 'Unknown zone' >&2; exit 1;; esac
cd "$2"
# Verify all artifacts before loading, then verify immutable IDs behind local tags.
sha256sum --check "$zone-SHA256SUMS"
docker image load --input "$zone-images.tar"
while read -r expected reference; do
  actual=$(docker image inspect --format '{{.Id}}' "$reference")
  actual=${actual%$'\r'} # Also support local verification with Docker.exe/Git Bash.
  test "$actual" = "$expected" || { echo "Image ID mismatch: $reference" >&2; exit 1; }
done < "$zone-imageids.txt"
echo "Verified $zone images; no application containers have been started."
