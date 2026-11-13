#!/usr/bin/env bash
# Empaqueta desktop/target/release/RevolutionMote como dist/RevolutionMote-Plus-x86_64.AppImage.
# Pensado para CI (ubuntu-22.04). Requiere: wget, file.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BIN="$ROOT/desktop/target/release/RevolutionMote"
OUT="$ROOT/dist"
APPDIR="$OUT/RevolutionMote.AppDir"

[[ -f "$BIN" ]] || { echo "Compila antes: cargo build --release"; exit 1; }

rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr/bin" "$APPDIR/usr/share/applications" "$APPDIR/usr/share/icons/hicolor/256x256/apps" "$OUT"

install -m 0755 "$BIN" "$APPDIR/usr/bin/RevolutionMote"
install -m 0644 "$ROOT/packaging/linux/RevolutionMote.desktop" "$APPDIR/usr/share/applications/RevolutionMote.desktop"
cp "$ROOT/packaging/linux/RevolutionMote.desktop" "$APPDIR/RevolutionMote.desktop"
if [[ -f "$ROOT/packaging/linux/revolutionmote.png" ]]; then
    install -m 0644 "$ROOT/packaging/linux/revolutionmote.png" "$APPDIR/usr/share/icons/hicolor/256x256/apps/revolutionmote.png"
    cp "$ROOT/packaging/linux/revolutionmote.png" "$APPDIR/revolutionmote.png"
fi

cat > "$APPDIR/AppRun" <<'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/RevolutionMote" "$@"
EOF
chmod +x "$APPDIR/AppRun"

TOOL="$OUT/appimagetool"
if [[ ! -x "$TOOL" ]]; then
    wget -q -O "$TOOL" "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
    chmod +x "$TOOL"
fi

ARCH=x86_64 "$TOOL" "$APPDIR" "$OUT/RevolutionMote-Plus-x86_64.AppImage"
echo "OK: $OUT/RevolutionMote-Plus-x86_64.AppImage"
