#!/usr/bin/env bash
# Empaqueta desktop/target/release/RevolutionMote como dist/RevolutionMote-Plus-linux-x86_64.tar.gz:
# el binario, el lanzador, el icono, la regla udev de uinput e install.sh.
# Para quien prefiera un tar.gz al AppImage (o en distros donde FUSE da
# guerra): descomprimir y ./install.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BIN="$ROOT/desktop/target/release/RevolutionMote"
OUT="$ROOT/dist"
STAGE="$OUT/RevolutionMote"
PKG="$ROOT/packaging/linux"

[[ -f "$BIN" ]] || { echo "Compila antes: cd desktop && cargo build --release"; exit 1; }

rm -rf "$STAGE"
mkdir -p "$STAGE" "$OUT"
install -m 0755 "$BIN" "$STAGE/RevolutionMote"
# Exact executable for verified in-place updates of user-owned tar installs.
install -m 0755 "$BIN" "$OUT/RevolutionMote-Plus-linux-x86_64"
install -m 0644 "$PKG/RevolutionMote.desktop" "$STAGE/RevolutionMote.desktop"
install -m 0644 "$PKG/revolutionmote.png" "$STAGE/revolutionmote.png"
install -m 0644 "$PKG/99-revolutionmote.rules" "$STAGE/99-revolutionmote.rules"
install -m 0755 "$PKG/install.sh" "$STAGE/install.sh"

tar -C "$OUT" -czf "$OUT/RevolutionMote-Plus-linux-x86_64.tar.gz" RevolutionMote
rm -rf "$STAGE"
echo "OK: $OUT/RevolutionMote-Plus-linux-x86_64.tar.gz"
