# Guía para agentes: RevolutionMote+

RevolutionMote+ convierte el móvil en mando, puntero, teclado y ratón para PC y
emuladores. Es un proyecto de **daniel27bc**, derivado de PepoMote (PepoTechYT,
GPL-3.0), del que partió en su versión 1.13.3. El original no ha recibido
cambios desde entonces: no hay nada que traer de él.

## Reglas que no se negocian

- **Autoría**: el autor es daniel27bc. Los commits van solo a su nombre
  (`git -c user.name="daniel27bc" -c user.email=<su correo> commit`). Nada de
  `Co-Authored-By`, ni firmas ni créditos de un asistente en commits, PR, código
  o documentación.
- **Ramas**: se trabaja en una rama `feat/*` (ahora `feat/v1.4`). No se toca
  `main` ni `release` sin que Daniel lo pida.
- **Idioma**: comentarios, commits, nombres de tests y textos en español. Cada
  texto de la app va en `values/strings.xml` (es) y `values-en/strings.xml`
  (en); un test comprueba que no falte ninguno.
- **Estilo**: comentarios que explican el porqué, lógica pura separada de la
  interfaz y con test propio (mira `HomeLayout`, `PadMacro`, `BtPadReport`).

## Dónde está cada cosa

| Carpeta | Qué es |
|---|---|
| `android/` | La app (Kotlin, Jetpack Compose). Paquete de código `dev.pepotech.pepomote`; id de la app `dev.daniel27bc.revolutionmote` |
| `desktop/` | El receptor de PC (Rust). El teclado, ratón y mando virtual solo en Windows (`src/input/windows_input.rs`) |
| `protocol/`, `pmp/` | El protocolo entre móvil y receptor |
| `docs/` | Guías de uso y `docs/releases/` (notas de versión) |
| `ios/`, `mobile-linux/`, `packaging/` | Otras plataformas y empaquetado |

En Android, dentro de `android/app/src/main/java/dev/pepotech/pepomote/`:

- `control/`: lógica sin interfaz (estado de botones `ButtonState`, perfiles
  `PadProfile`, preferencias `AppPrefs`, Bluetooth `BtHid`/`BtPad`, teclado y
  ratón `Kbm`, giroscopio `GyroAim`, macros `PadMacro`).
- `ui/screens/`: pantallas (`HomeScreen`, `GamePadScreen`, `KbmScreen`,
  `SettingsScreen`, `CustomPadEditor`).
- `service/`: enlace con el receptor (`LinkState`, `Route`).
- `server/`: el servidor Android (la app haciendo de receptor en otro Android).
- `MainActivity.kt`: navegación entre pantallas.

## Cómo se compila y se prueba

No hace falta compilar en local: lo hace GitHub Actions.

1. Sube los commits a la rama `feat/*`.
2. El flujo `android` ejecuta tests, lint y genera el APK (unos 6 minutos). El
   flujo `desktop` compila el receptor si cambió `desktop/`.
3. Consulta el resultado con `gh run list -R daniel27bc/RevolutionMote-Plus`
   y, si falla, `gh run view <id> --log-failed`.
4. El APK de prueba, `BUILD.txt` y las capturas de pantalla quedan en la rama
   `ci-out/<rama>` (por ejemplo `ci-out/feat/v1.4`). El receptor de Windows
   sale como artefacto `RevolutionMote-Plus-windows` del run de `desktop`, junto con el DMG de macOS (`RevolutionMote-Plus-macos`).
   El flujo `ios` genera `RevolutionMote-Plus-ios` con el IPA sin firmar.

Para ver un cambio de interfaz, añade una captura en
`android/app/src/test/.../ui/shots/ScreenshotTest.kt` y mírala en `ci-out`.

Avisos:

- Dos tests de red fallan de vez en cuando sin motivo
  (`ReceiverCoreRetroArchTest`, `LinkForegroundServiceTest`). Si solo fallan
  esos, relanza antes de buscar un error propio.
- Nada se puede probar en un móvil real desde la CI. Lo que dependa de
  hardware (Bluetooth, sensores) hay que decir claramente que está sin probar.
- Cambiar el descriptor HID de `BtHid` obliga a volver a emparejar el móvil
  con cada equipo. Agrupa esos cambios.

## Estado de la versión 1.4 (rama `feat/v1.4`)

Hecho y compilado en CI, sin probar en un dispositivo:

- Inicio con barra de navegación: pestaña Inicio («Continuar», favoritos
  fijables, Mandos, Otras herramientas), pestaña Conexión (equipos
  vinculados, encender el PC, conectar por QR, servidor) y acceso a Ajustes.
- Teclado y ratón con pestañas: Touchpad, Atajos, Multimedia, Numérico,
  Presentar.
- Mando universal: perfiles por QR, gestos (volante y agitar), macros, capa
  Shift.
- Encender el PC por la red (Wake-on-LAN).
- Mando universal por Bluetooth, sin receptor (`BtPad`, `BtPadScreen`).

Pendiente o por decidir con Daniel: ver `docs/HANDOFF-1.4.md`.
