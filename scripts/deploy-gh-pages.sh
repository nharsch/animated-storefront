#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PUBLIC_DIR="$REPO_ROOT/public"

cd "$PUBLIC_DIR"

git init -q
git checkout -b gh-pages
git add -A
git commit -q -m "Deploy $(date -u +%Y-%m-%dT%H:%M:%SZ)"
git remote add origin "$(cd "$REPO_ROOT" && git remote get-url origin)"
git push --force origin gh-pages

rm -rf "$PUBLIC_DIR/.git"

echo "==> Deployed to https://nharsch.github.io/animated-storefront/"
