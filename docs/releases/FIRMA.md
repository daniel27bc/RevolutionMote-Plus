# Firma del APK de Android

Desde la 1.4.3 todas las versiones de Android se firman con la misma clave.
Así Android instala cada versión nueva encima de la anterior (sin
desinstalar ni perder ajustes) y el aviso «Hay una versión nueva» de la app
puede actualizarla sola.

- Huella SHA-256 del certificado:
  `20:84:23:1C:A3:4D:C0:52:E5:E5:28:D6:92:66:86:43:B0:BE:03:A3:DE:DB:52:F3:12:61:D0:51:7B:A1:77:44`
- La clave vive en los secretos del repositorio (`KEYSTORE_B64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). El flujo `release` no
  publica nada sin ellos y comprueba que el APK lleve esta huella.
- **Guarda una copia del archivo `.jks` y su contraseña fuera de GitHub.** Si
  se pierde, ninguna versión nueva podrá instalarse encima de las anteriores.
- Hasta la 1.4.2 cada APK salía firmado con una clave distinta: para pasar a
  la 1.4.3 hay que desinstalar una última vez.
