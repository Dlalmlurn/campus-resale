#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

pushd "$ROOT_DIR/backend" >/dev/null
mvn test
popd >/dev/null

pushd "$ROOT_DIR/frontend" >/dev/null
npm test -- --run
npm run build
popd >/dev/null
