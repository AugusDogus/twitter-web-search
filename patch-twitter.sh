#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
if [[ $# -ne 2 ]]; then
  echo "Usage: ./patch-twitter.sh INPUT.apk[m] OUTPUT.apk" >&2
  exit 2
fi
if [[ -e "$2" ]]; then
  echo "Output already exists: $2. Choose a new output path." >&2
  exit 2
fi
java -Xmx2g -jar deps/morphe-desktop-1.15.0-all.jar patch \
  --patches patches/build/libs/newx-web-search-1.0.0.mpp \
  --exclusive --enable 'NewX: Web search selected text' \
  --unsigned --out "$2" "$1"
