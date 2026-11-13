# Traspaso de la versión 1.4

Estado a 13 de noviembre de 2026. Rama de trabajo: `feat/v1.4`.

## Lo último que se hizo

El mando universal por Bluetooth (sin receptor). El primer intento falló en
la CI por un orden de inicialización en `BtPadReport` (`NEUTRAL` se creaba
antes que `BUTTONS`); está corregido en el commit siguiente. La CI de ese commit (`fa544fd`) quedó en verde y la
captura `22_mando_bluetooth.png` de `ci-out/feat/v1.4` muestra el mando
universal con la cabecera «Mando Bluetooth».

Después se añadió la barra de navegación del inicio (commit `6d7e81a`, CI en
verde): pestañas Inicio (Favoritos, Mandos, Otras herramientas) y Conexión
(vincular por QR o alojar como servidor), más el acceso a Ajustes. Sin nada
vinculado ni servidor en marcha, la app se abre por Conexión
(`HomeLayout.startsOnConnection`). La pastilla del mando Bluetooth ya dice
«Mando Bluetooth».

Cosas del mando Bluetooth que conviene saber:

- En Windows aparece como mando genérico (DirectInput), no como mando de Xbox.
  Los juegos que solo aceptan XInput necesitan Steam Input u otro traductor.
- Los gatillos son botones, no ejes analógicos.
- No se ha probado con ningún equipo real.

## Por probar en un móvil (nada está verificado en hardware)

- Inicio nuevo y favoritos.
- Pestañas de Teclado y ratón, por la app y por Bluetooth. Las teclas
  multimedia y los operadores del numérico por la app necesitan el receptor
  de Windows nuevo.
- Volante: el sentido de giro se dedujo sin dispositivo; hay un interruptor
  para invertirlo.
- Agitar, macros y Shift.
- Bluetooth: como cambió el descriptor (multimedia y mando), hay que volver a
  emparejar el móvil con cada equipo.

## Ideas comentadas y aún sin decidir

Daniel tiene que decir cuáles quiere y cuáles no:

1. Widget de escritorio con «Continuar».
2. Perfiles por juego automáticos (el receptor avisa de qué juego está en
   primer plano). Toca protocolo y receptor.
3. Inicio que se adapta a lo que hay abierto en el PC. Toca receptor.
4. Portapapeles compartido entre móvil y PC. Toca receptor.
5. Segundo móvil como complemento del mismo jugador. Toca receptor.
6. Micrófono del móvil como micrófono del PC (tipo WO Mic). Necesita un
   controlador de audio virtual en Windows; lo realista es apoyarse en
   VB-Cable.
7. Cámara del móvil como webcam. Se recomendó dejarla fuera.
8. Conexión por cable con el receptor, usando la conexión USB compartida del
   móvil. Falta comprobar que el emparejamiento elige bien esa red.
9. DSU directo: que Dolphin, Cemu o los emuladores de Switch lean el móvil
   sin receptor, también por cable. La app ya tiene un servidor DSU para el
   modo Android (`server/`).
10. Ideas del fork HolmanAlv/PepoMote-AndroidHost-Dolphin: party por QR,
    compartir los INI como archivo, herramienta de diagnóstico DSU. A Daniel
    no le parecieron atractivas.
11. Modo ahorro: se dio por cubierto con el tema OLED.

## Antes de publicar

- Notas de versión en `docs/releases/`.
- Revisar README (es/en) para las funciones nuevas.
- Pasar `feat/v1.4` a `main` solo cuando Daniel lo pida.
