fn main() {
    // VB-CABLE (el micrófono del móvil en Windows) viaja dentro del exe si
    // está en packaging/windows: la CI lo descarga antes de compilar
    // (packaging/windows/README.md). Sin él, el receptor explica al móvil
    // dónde conseguirlo en vez de instalarlo.
    println!("cargo:rustc-check-cfg=cfg(vbcable_pack)");
    let pack = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../packaging/windows/VBCABLE_Driver_Pack.zip");
    println!("cargo:rerun-if-changed={}", pack.display());
    if pack.is_file() {
        println!("cargo:rustc-cfg=vbcable_pack");
    }
    // Icono y metadatos de versión del exe (solo target Windows)
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("windows") {
        let mut res = winresource::WindowsResource::new();
        res.set_icon("app.ico");
        res.set("ProductName", "RevolutionMote+");
        // Es el «Nombre» que enseñan la alerta del firewall y la ventana de
        // permisos de Windows. El «Editor» de esas ventanas NO sale de aquí:
        // solo de una firma digital de confianza (sin ella, «Desconocido»)
        res.set("FileDescription", "RevolutionMote+ - Apunta y Juega");
        res.set("CompanyName", "daniel27bc");
        res.set("LegalCopyright", "GPL-3.0-or-later · PepoTech y daniel27bc");
        res.set("OriginalFilename", "RevolutionMote.exe");
        if let Err(e) = res.compile() {
            println!("cargo:warning=sin recursos de Windows: {e}");
        }
    }
}
