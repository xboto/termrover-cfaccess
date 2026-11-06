#!/usr/bin/env bash
set -euo pipefail
VER=2026.10.7
SHA=3df43aedc9ff291d1735b5cb576cb97e0b157488796db2e32b28aecabb445775
DST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/jniLibs/arm64-v8a/libcloudflared.so"
mkdir -p "$(dirname "$DST")"
if [ -f "$DST" ] && [ "$(shasum -a 256 "$DST" | cut -d' ' -f1)" = "$SHA" ]; then
  echo "ok (already present): $DST"; exit 0
fi
curl -fsSL -o "$DST.tmp" "https://github.com/cloudflare/cloudflared/releases/download/$VER/cloudflared-linux-arm64"
GOT="$(shasum -a 256 "$DST.tmp" | cut -d' ' -f1)"
if [ "$GOT" != "$SHA" ]; then rm -f "$DST.tmp"; echo "SHA-256 mismatch: $GOT" >&2; exit 1; fi
mv "$DST.tmp" "$DST"
echo "ok: $DST"
