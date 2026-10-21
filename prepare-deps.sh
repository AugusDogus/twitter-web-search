#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
mkdir -p deps
dependency=deps/morphe-desktop-1.15.0-all.jar
if [[ ! -f "$dependency" ]]; then
  curl --fail --location --retry 3 \
    https://github.com/MorpheApp/morphe-desktop/releases/download/v1.15.0/morphe-desktop-1.15.0-all.jar \
    --output "$dependency"
fi
echo '727e3744aa5c0006474590de6f4041bd55edc59f3d6cb9b596e95f7116384506  deps/morphe-desktop-1.15.0-all.jar' | sha256sum --check
