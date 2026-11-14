# Driver del mando virtual (Windows)

`ViGEmBus_1.22.0_x64_x86_arm64.exe` es el instalador **oficial y sin
modificar** de ViGEmBus, el driver de bus que crea el mando de Xbox 360
virtual con el que funcionan el mando universal y la vibración de los juegos.
Va embebido en `RevolutionMote.exe` (`desktop/src/rumble/vigem_setup.rs`) y
RevolutionMote+ lo ofrece la primera vez que eliges el mando Xbox virtual, con la única ventana
de permiso de administrador que Windows exige para cualquier driver.

- Origen: <https://github.com/nefarius/ViGEmBus/releases/tag/v1.22.0>
  (última y definitiva versión: el proyecto se retiró en 2023 por un conflicto
  de marca; las instalaciones existentes siguen funcionando).
- Tamaño: 6 278 576 bytes.
- SHA-256: `89220a7865076b342892f98865f3499fb7c4cfd673159e89d352c360fd014c6a`
  (el receptor lo comprueba en sus tests: `vigem_setup::el_instalador_embebido_es_el_oficial`).
- Firma Authenticode válida de «Nefarius Software Solutions e.U.» (DigiCert
  Trusted G4 Code Signing), huella `1F431092EC96A80B41AB5317F53AC02EA6F9B89B`.
- Licencia: BSD-3-Clause, `ViGEmBus-LICENSE.txt` (se redistribuye el binario
  tal cual, con su aviso, como pide la licencia).
- Instalación silenciosa (Advanced Installer): `/exenoui /qn /norestart`.

**No se puede tocar ni recompilar:** es un driver de núcleo firmado. Windows
10/11 solo carga drivers con firma de Microsoft (atestación), y esa firma es
la de Nefarius sobre estos bytes exactos. Cualquier cambio dejaría el driver
sin cargar. Lo que sí es nuestro es el cliente (crate `vigem-client`, Rust
puro, por IOCTL) y todo lo que hay encima.

# Cable de audio del micrófono (Windows)

El micrófono del móvil llega a los programas de Windows por **VB-CABLE**, el
cable de audio virtual de VB-Audio (<https://vb-cable.com>). VB-CABLE es
donationware: si te sirve, apoya a su autor desde su web.

- Paquete oficial: `VBCABLE_Driver_Pack46.zip`, de
  <https://download.vb-audio.com/Download_CABLE/VBCABLE_Driver_Pack46.zip>.
- No está en el repositorio: el flujo `desktop` lo descarga en
  `packaging/windows/VBCABLE_Driver_Pack.zip` antes de compilar y el exe lo
  lleva dentro (`desktop/src/net/vbcable.rs`). El log de ese paso enseña su
  SHA-256 y los archivos que trae. Versión 46, comprobada el 14/11/2026:
  SHA-256 `BC44E8974191A5A2427D6C979E2DAB08DBB09925F3F435DFA2F0BA3E45B6EB90`,
  con `VBCABLE_Setup_x64.exe` y `VBCABLE_Setup.exe`.
- Licencia: VB-Audio permite incluir el paquete sin modificar en programas
  gratuitos siempre que se diga que es de VB-Audio y que es donationware
  (<https://vb-audio.com/Services/licensing.htm>). La app lo dice en el
  diálogo de instalación y aquí. En empresas o centros donde los usuarios no
  puedan ver de dónde viene, VB-Audio pide comprar licencias.
- Instalación desde el móvil: el receptor desempaqueta el paquete en su
  carpeta de configuración (`VB-CABLE`) y lanza `VBCABLE_Setup_x64.exe -i -h`
  con la ventana de permiso de administrador. Si no se puede, abre esa
  carpeta en el PC y el móvil explica cómo instalarlo a mano.
- Sin el paquete (compilado en local sin descargarlo), el receptor funciona
  igual y el móvil manda a la web de VB-CABLE.
