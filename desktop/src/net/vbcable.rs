//! VB-CABLE, el cable de audio virtual por el que el micrófono del móvil
//! llega a los programas de Windows, viaja DENTRO del exe: el paquete
//! oficial de VB-Audio sin tocar (`packaging/windows/README.md`). VB-Audio
//! permite incluirlo así en programas gratuitos siempre que se diga de quién
//! es y que es donationware (www.vb-cable.com); la app y la guía lo dicen.
//!
//! Desde el móvil se pide instalarlo: el receptor desempaqueta el paquete,
//! lanza su instalador con la ventana de permiso de Windows y espera a que
//! aparezca «CABLE Input». Si eso no se puede (el exe se compiló sin el
//! paquete, el usuario dijo que no o el instalador falla), el receptor abre
//! en el PC la carpeta con el instalador y el móvil explica qué hacer.

use std::path::PathBuf;
use std::time::{Duration, Instant};

#[cfg(vbcable_pack)]
const PACK: Option<&[u8]> = Some(include_bytes!("../../../packaging/windows/VBCABLE_Driver_Pack.zip"));
#[cfg(not(vbcable_pack))]
const PACK: Option<&[u8]> = None;

/// Instalación silenciosa del instalador de VB-CABLE: instalar y sin su
/// ventana (solo queda la de permiso de Windows).
const SETUP_ARGS: &str = "-i -h";

/// Lo que se tarda, como mucho, en ver «CABLE Input» tras el instalador.
const APPEAR_LIMIT: Duration = Duration::from_secs(20);

/// Cómo acabó la instalación pedida desde el móvil.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(super) enum Install {
    /// Ya está «CABLE Input»: el micrófono puede funcionar.
    Installed,
    /// El instalador terminó pero Windows aún no enseña el cable: hay que
    /// reiniciar el PC (lo pide el propio VB-Audio en algunos equipos).
    NeedsRestart,
    /// Dijo que no en la ventana de permiso de Windows.
    Declined,
    /// No se pudo instalar solo: el instalador está en esta carpeta (ya
    /// abierta en el PC), o `None` si este receptor no lo trae.
    Manual(Option<PathBuf>),
}

/// Si este receptor trae VB-CABLE dentro.
pub(super) fn bundled() -> bool {
    PACK.is_some()
}

/// Carpeta donde se desempaqueta el instalador.
fn folder() -> Option<PathBuf> {
    crate::state::config_dir().map(|d| d.join("VB-CABLE"))
}

/// Desempaqueta el paquete (se reescribe siempre: así nunca se lanza un
/// archivo que otro haya tocado).
fn unpack() -> Result<PathBuf, String> {
    let pack = PACK.ok_or_else(|| "receptor sin VB-CABLE".to_owned())?;
    let dir = folder().ok_or_else(|| "sin carpeta de configuración".to_owned())?;
    std::fs::create_dir_all(&dir).map_err(|e| format!("{}: {e}", dir.display()))?;
    let mut zip = zip::ZipArchive::new(std::io::Cursor::new(pack)).map_err(|e| format!("zip: {e}"))?;
    for i in 0..zip.len() {
        let mut entry = zip.by_index(i).map_err(|e| format!("zip: {e}"))?;
        // Solo nombres dentro de la carpeta (nada de «..\»)
        let Some(name) = entry.enclosed_name() else { continue };
        let target = dir.join(name);
        if entry.is_dir() {
            let _ = std::fs::create_dir_all(&target);
            continue;
        }
        if let Some(parent) = target.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        let mut out = std::fs::File::create(&target).map_err(|e| format!("{}: {e}", target.display()))?;
        std::io::copy(&mut entry, &mut out).map_err(|e| format!("{}: {e}", target.display()))?;
    }
    Ok(dir)
}

/// El instalador que toca en este Windows, dentro de la carpeta.
fn setup_in(dir: &std::path::Path) -> Option<PathBuf> {
    let names: &[&str] = if cfg!(target_arch = "x86") {
        &["VBCABLE_Setup.exe"]
    } else {
        &["VBCABLE_Setup_x64.exe", "VBCABLE_Setup.exe"]
    };
    names.iter().map(|n| dir.join(n)).find(|p| p.is_file())
}

/// Abre la carpeta en el Explorador del PC, para hacerlo a mano.
fn reveal(dir: &std::path::Path) {
    let _ = std::process::Command::new("explorer.exe").arg(dir).spawn();
}

/// Instala VB-CABLE desde el exe. Bloquea hasta que termina (minutos si el
/// usuario tarda en aceptar el permiso). `present` dice si ya se ve el cable.
pub(super) fn install(present: impl Fn() -> bool) -> Install {
    if present() {
        return Install::Installed;
    }
    if !bundled() {
        return Install::Manual(None);
    }
    let dir = match unpack() {
        Ok(dir) => dir,
        Err(e) => {
            crate::log_line!("Micrófono: no se pudo desempaquetar VB-CABLE: {e}");
            return Install::Manual(None);
        }
    };
    let Some(setup) = setup_in(&dir) else {
        crate::log_line!("Micrófono: el paquete de VB-CABLE no trae instalador para este Windows");
        reveal(&dir);
        return Install::Manual(Some(dir));
    };
    crate::log_line!("Micrófono: se instala VB-CABLE ({})", setup.display());
    match crate::rumble::vigem_setup::run_elevated(&setup, SETUP_ARGS) {
        Ok(crate::rumble::vigem_setup::Elevated::Declined) => Install::Declined,
        Ok(crate::rumble::vigem_setup::Elevated::Exited(code)) => {
            crate::log_line!("Micrófono: el instalador de VB-CABLE terminó con {code}");
            let start = Instant::now();
            while start.elapsed() < APPEAR_LIMIT {
                if present() {
                    return Install::Installed;
                }
                std::thread::sleep(Duration::from_millis(500));
            }
            if code == 0 { Install::NeedsRestart } else {
                reveal(&dir);
                Install::Manual(Some(dir))
            }
        }
        Err(e) => {
            crate::log_line!("Micrófono: no se pudo lanzar el instalador de VB-CABLE: {e}");
            reveal(&dir);
            Install::Manual(Some(dir))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn el_instalador_se_elige_por_arquitectura() {
        let dir = std::env::temp_dir().join(format!("rm-vbcable-{}", std::process::id()));
        let _ = std::fs::create_dir_all(&dir);
        assert_eq!(setup_in(&dir), None, "sin instalador no se lanza nada");
        std::fs::write(dir.join("VBCABLE_Setup.exe"), b"MZ").unwrap();
        std::fs::write(dir.join("VBCABLE_Setup_x64.exe"), b"MZ").unwrap();
        let chosen = setup_in(&dir).unwrap();
        let expected = if cfg!(target_arch = "x86") { "VBCABLE_Setup.exe" } else { "VBCABLE_Setup_x64.exe" };
        assert_eq!(chosen.file_name().unwrap(), expected);
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn con_el_cable_ya_puesto_no_se_instala_nada() {
        assert_eq!(install(|| true), Install::Installed);
    }

    /// En la CI de Windows el paquete se descarga antes de compilar: si
    /// falta, el receptor saldría sin poder instalar el micrófono.
    #[test]
    fn el_paquete_trae_el_instalador() {
        let Some(pack) = PACK else { return };
        let zip = zip::ZipArchive::new(std::io::Cursor::new(pack)).expect("zip válido");
        let names: Vec<_> = zip.file_names().map(str::to_owned).collect();
        assert!(names.iter().any(|n| n == "VBCABLE_Setup_x64.exe"), "{names:?}");
    }
}
