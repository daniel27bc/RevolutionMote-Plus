#!/usr/bin/env bash
# RevolutionMote+ — instalación limpia en Linux.
# Hace dos cosas: instala la regla udev de uinput (necesita sudo una vez)
# y deja RevolutionMote+ (el AppImage, o el binario del tar.gz) en ~/.local/bin con
# su lanzador de escritorio. Sin argumento: el binario `RevolutionMote+` que va
# junto a este script (tar.gz) o, si no, RevolutionMote-Plus-x86_64.AppImage.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APPIMAGE="${1:-}"
if [[ -z "$APPIMAGE" ]]; then
    if [[ -f "$SCRIPT_DIR/RevolutionMote" ]]; then
        APPIMAGE="$SCRIPT_DIR/RevolutionMote"
    else
        APPIMAGE="RevolutionMote-Plus-x86_64.AppImage"
    fi
fi

if [[ ! -f "$APPIMAGE" ]]; then
    echo "Uso: $0 [ruta a RevolutionMote-Plus-x86_64.AppImage o al binario RevolutionMote+ del tar.gz]"
    echo "No encuentro '$APPIMAGE'."
    exit 1
fi

echo "==> Regla udev para /dev/uinput (se pide sudo una sola vez)"
sudo install -m 0644 "$SCRIPT_DIR/99-revolutionmote.rules" /etc/udev/rules.d/99-revolutionmote.rules
sudo udevadm control --reload-rules
sudo udevadm trigger /dev/uinput 2>/dev/null || true
sudo modprobe uinput 2>/dev/null || true
echo 'uinput' | sudo tee /etc/modules-load.d/revolutionmote.conf >/dev/null
# ACL directa para no tener que cerrar sesión (la regla uaccess cubre las siguientes)
sudo setfacl -m "u:${SUDO_UID:-$(id -u)}:rw" /dev/uinput 2>/dev/null || true

PORT="${PEPOMOTE_PORT:-26761}"
echo "==> Firewall: abriendo el puerto $PORT (TCP+UDP) si hay uno activo"
if command -v ufw >/dev/null 2>&1 && sudo ufw status 2>/dev/null | grep -q "^Status: active\|^Estado: activo"; then
    sudo ufw allow "$PORT/tcp" comment 'RevolutionMote+' >/dev/null
    sudo ufw allow "$PORT/udp" comment 'RevolutionMote+' >/dev/null
    # mDNS: solo hace falta para el autodescubrimiento; el QR funciona sin él
    sudo ufw allow 5353/udp comment 'mDNS (RevolutionMote+)' >/dev/null || true
    echo "    ufw: puertos abiertos"
elif command -v firewall-cmd >/dev/null 2>&1 && sudo firewall-cmd --state >/dev/null 2>&1; then
    sudo firewall-cmd --permanent --add-port="$PORT/tcp" --add-port="$PORT/udp" >/dev/null
    sudo firewall-cmd --permanent --add-service=mdns >/dev/null 2>&1 || true
    sudo firewall-cmd --reload >/dev/null
    echo "    firewalld: puertos abiertos"
else
    echo "    sin ufw/firewalld activos — nada que abrir"
fi

echo "==> Instalando RevolutionMote+ en ~/.local/bin"
mkdir -p "$HOME/.local/bin" "$HOME/.local/share/applications" "$HOME/.local/share/icons/hicolor/256x256/apps"
install -m 0755 "$APPIMAGE" "$HOME/.local/bin/RevolutionMote"
if [[ -f "$SCRIPT_DIR/revolutionmote.png" ]]; then
    install -m 0644 "$SCRIPT_DIR/revolutionmote.png" "$HOME/.local/share/icons/hicolor/256x256/apps/revolutionmote.png"
fi
sed "s|^Exec=.*|Exec=$HOME/.local/bin/RevolutionMote|" "$SCRIPT_DIR/RevolutionMote.desktop" \
    > "$HOME/.local/share/applications/RevolutionMote.desktop"

echo
echo "Listo. Abre 'RevolutionMote+' desde tu lanzador de aplicaciones."
echo "En Sway, Hyprland, MangoWC y demás compositores wlroots el cursor va por Wayland: no necesita nada de uinput."
echo "Solo si /dev/uinput siguiera sin permiso (GNOME, KDE, X11): cierra sesión y vuelve a entrar (la regla uaccess se aplica al iniciar sesión)."
