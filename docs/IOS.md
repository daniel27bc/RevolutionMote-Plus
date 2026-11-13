# RevolutionMote+ para iPhone y iPad

La app requiere iOS o iPadOS 15 o posterior. Esta compilación incluye el
puntero, los mandos de Dolphin, Cemu, Eden y RetroArch y el emparejamiento
con el receptor por red local. La paridad con las funciones nuevas de
Android se comprueba aparte.

## Descargar e instalar

1. Abre [GitHub Actions](https://github.com/daniel27bc/RevolutionMote-Plus/actions/workflows/ios.yml)
   y elige una ejecución verde de `feat/v1.4`.
2. Descarga `RevolutionMote-Plus-ios` y extrae `RevolutionMote-Plus.ipa`.
   El IPA sale sin firmar. Impórtalo en AltStore Classic, SideStore o
   Sideloadly para que lo firme tu cuenta.
3. Abre RevolutionMote+ en el ordenador y escanea el QR con el móvil.
   Concede a la app acceso a cámara y red local cuando lo solicite.

La descarga de pruebas se distribuye como archivo IPA. Los avisos de
actualización consultan las versiones de RevolutionMote+ en GitHub.

## Compilar en un Mac

```sh
brew install xcodegen
cd ios
xcodegen generate
open PepoMote.xcodeproj
```

Para ejecutar en un dispositivo desde Xcode, selecciona tu equipo en
Signing & Capabilities. GitHub Actions ejecuta los tests en el simulador
y compila también para dispositivo.
