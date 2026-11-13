# Receptor RevolutionMote+ en macOS

La compilación de prueba requiere un Mac con Apple Silicon y macOS 13
o posterior.

1. En [GitHub Actions](https://github.com/daniel27bc/RevolutionMote-Plus/actions/workflows/desktop.yml),
   abre una ejecución verde de `feat/v1.4`. Descarga
   `RevolutionMote-Plus-macos` y extrae el DMG.
2. Abre `RevolutionMote-Plus-macOS.dmg`, arrastra **RevolutionMote+**
   a **Aplicaciones** y abre el receptor.
3. Con el móvil y el Mac en la misma red, escanea el QR que aparece en
   el receptor.

El receptor indica en su ventana los permisos de macOS cuando hacen
falta. El puntero y el teclado usan Accesibilidad. La pantalla de Cemu
usa Grabación de pantalla. Las compilaciones de prueba pueden estar
firmadas ad hoc y su empaquetado en CI no incluye notarización.

Para obtener el informe de diagnóstico:

```sh
"/Applications/RevolutionMote+.app/Contents/MacOS/RevolutionMote" --diag
```

Para compilar y empaquetar localmente en un Mac:

```sh
cargo build --release --manifest-path desktop/Cargo.toml
bash packaging/macos/bundle.sh
```
