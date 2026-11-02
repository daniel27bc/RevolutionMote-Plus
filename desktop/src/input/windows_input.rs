use super::text_plan::{win_text_plan, WinKey};
use super::{Injector, KeyCode, MouseButton, TypeReport};
use windows::Win32::UI::Input::KeyboardAndMouse::{
    MapVirtualKeyW, KEYEVENTF_EXTENDEDKEY, KEYEVENTF_SCANCODE, MAPVK_VK_TO_VSC, MOUSEEVENTF_MIDDLEDOWN,
    MOUSEEVENTF_MIDDLEUP,
};
use windows::Win32::UI::Input::KeyboardAndMouse::{
    SendInput, INPUT, INPUT_0, INPUT_KEYBOARD, INPUT_MOUSE, KEYBDINPUT, KEYEVENTF_KEYUP,
    MOUSEEVENTF_ABSOLUTE, MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP, MOUSEEVENTF_MOVE,
    MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP, MOUSEEVENTF_VIRTUALDESK, MOUSEEVENTF_WHEEL,
    VkKeyScanW, KEYEVENTF_UNICODE, MOUSEINPUT, MOUSE_EVENT_FLAGS, VIRTUAL_KEY, VK_BACK,
    VK_BROWSER_BACK, VK_BROWSER_FORWARD, VK_DOWN,
    VK_ESCAPE, VK_LEFT, VK_MEDIA_NEXT_TRACK, VK_MEDIA_PLAY_PAUSE, VK_MEDIA_PREV_TRACK, VK_RETURN,
    VK_RIGHT, VK_SHIFT, VK_SPACE, VK_UP, VK_VOLUME_DOWN, VK_VOLUME_MUTE, VK_VOLUME_UP,
};
use windows::Win32::Foundation::{HWND, POINT};
use windows::Win32::System::Threading::GetCurrentThreadId;
use windows::Win32::UI::Input::KeyboardAndMouse::VK_MENU;
use windows::Win32::System::Threading::AttachThreadInput;
use windows::Win32::UI::WindowsAndMessaging::{
    GetAncestor, GetCursorPos, GetForegroundWindow, GetSystemMetrics, GetWindowThreadProcessId,
    SetForegroundWindow, WindowFromPoint, GA_ROOT, SM_CXSCREEN, SM_CXVIRTUALSCREEN, SM_CYSCREEN,
    SM_CYVIRTUALSCREEN, SM_XVIRTUALSCREEN, SM_YVIRTUALSCREEN,
};

/// Cómo se lleva a primer plano la ventana sobre la que cae un clic cuando
/// `SetForegroundWindow` a secas no lo consigue.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum ActivationMode {
    /// Un `SendInput` vacío (movimiento nulo) y otro `SetForegroundWindow`:
    /// inyectar entrada es lo que da al proceso el derecho de activar.
    Modern,
    /// Vía anterior a 1.6: `AttachThreadInput` al hilo en primer plano y,
    /// si tampoco, la pulsación de ALT. `PEPOMOTE_WIN_ACTIVATE=legacy`.
    Legacy,
}

pub fn activation_mode(env: Option<&str>) -> ActivationMode {
    match env.map(|v| v.trim().to_ascii_lowercase()).as_deref() {
        Some("legacy") | Some("old") => ActivationMode::Legacy,
        _ => ActivationMode::Modern,
    }
}

pub struct WinInjector {
    activation: ActivationMode,
}

/// Activación para texto dirigido a un emulador, sin AttachThreadInput ni ALT.
pub(crate) fn focus_for_text(hwnd: HWND) -> bool {
    use windows::Win32::UI::WindowsAndMessaging::{IsIconic, ShowWindowAsync, SW_RESTORE};
    unsafe {
        if IsIconic(hwnd).as_bool() {let _=ShowWindowAsync(hwnd,SW_RESTORE);}
        if GetForegroundWindow()==hwnd {return true;}
        if !SetForegroundWindow(hwnd).as_bool() {
            WinInjector {activation:ActivationMode::Modern}.send_mouse(0,0,0,MOUSEEVENTF_MOVE);
            let _=SetForegroundWindow(hwnd);
        }
        GetForegroundWindow()==hwnd
    }
}

impl WinInjector {
    pub fn new() -> Self {
        let activation = activation_mode(std::env::var("PEPOMOTE_WIN_ACTIVATE").ok().as_deref());
        if activation != ActivationMode::Modern {
            crate::log_line!("Activación de ventanas al hacer clic: {activation:?} (PEPOMOTE_WIN_ACTIVATE)");
        }
        Self { activation }
    }

    fn send_mouse(&self, dx: i32, dy: i32, data: i32, flags: MOUSE_EVENT_FLAGS) {
        let input = INPUT {
            r#type: INPUT_MOUSE,
            Anonymous: INPUT_0 {
                mi: MOUSEINPUT {
                    dx,
                    dy,
                    mouseData: data as u32,
                    dwFlags: flags,
                    time: 0,
                    dwExtraInfo: 0,
                },
            },
        };
        unsafe {
            SendInput(&[input], std::mem::size_of::<INPUT>() as i32);
        }
    }

    /// Un clic de verdad lleva a primer plano la ventana sobre la que cae.
    /// Con un clic inyectado Windows no siempre lo hace (el derecho de
    /// primer plano lo tiene el proceso que «proveyó la última entrada»): se
    /// activa a mano, como haría el ratón. Primero por las buenas; si el
    /// sistema se resiste, tras un `SendInput` vacío (movimiento nulo), que
    /// es justo lo que convierte a este proceso en proveedor de entrada con
    /// derecho a activar. Ya no se usa `AttachThreadInput` (colgaba este
    /// hilo, el de la telemetría, si el hilo en primer plano no atendía
    /// mensajes: cursor y DSU parados) ni la pulsación de ALT (activaba
    /// barras de menú y dejaba ALT «pegado» en juegos): las dos cosas solo
    /// entraban con PepoMote sin foco, que es justo cuando fallaba.
    fn activate_window_under_cursor(&self) {
        unsafe {
            let mut pt = POINT::default();
            if GetCursorPos(&mut pt).is_err() {
                return;
            }
            let hit = WindowFromPoint(pt);
            if hit.0.is_null() {
                return;
            }
            let mut root = GetAncestor(hit, GA_ROOT);
            if root.0.is_null() {
                root = hit;
            }
            let fg = GetForegroundWindow();
            if root == fg {
                return;
            }
            if SetForegroundWindow(root).as_bool() {
                return;
            }
            match self.activation {
                ActivationMode::Modern => {
                    self.send_mouse(0, 0, 0, MOUSEEVENTF_MOVE);
                    let _ = SetForegroundWindow(root);
                }
                ActivationMode::Legacy => self.activate_legacy(root, fg),
            }
        }
    }

    /// Vía anterior a 1.6 (`PEPOMOTE_WIN_ACTIVATE=legacy`): cola de
    /// entrada compartida con el hilo en primer plano y, si tampoco, la
    /// pulsación de ALT que da el derecho, soltándola ya activada.
    unsafe fn activate_legacy(&self, root: HWND, fg: HWND) {
        let mut fg_thread = 0;
        if !fg.0.is_null() {
            fg_thread = GetWindowThreadProcessId(fg, None);
        }
        let me = GetCurrentThreadId();
        let attached = fg_thread != 0 && fg_thread != me && AttachThreadInput(me, fg_thread, true).as_bool();
        let ok = SetForegroundWindow(root).as_bool();
        if attached {
            let _ = AttachThreadInput(me, fg_thread, false);
        }
        if ok {
            return;
        }
        self.send_key(VK_MENU, true);
        let _ = SetForegroundWindow(root);
        self.send_key(VK_MENU, false);
    }

    fn send_key(&self, vk: VIRTUAL_KEY, down: bool) {
        let input = INPUT {
            r#type: INPUT_KEYBOARD,
            Anonymous: INPUT_0 {
                ki: KEYBDINPUT {
                    wVk: vk,
                    wScan: 0,
                    dwFlags: if down {
                        Default::default()
                    } else {
                        KEYEVENTF_KEYUP
                    },
                    time: 0,
                    dwExtraInfo: 0,
                },
            },
        };
        unsafe {
            SendInput(&[input], std::mem::size_of::<INPUT>() as i32);
        }
    }
}

/// Evento de teclado para `SendInput`: unidad UTF-16 (texto) o tecla
/// virtual (Intro y retroceso, que no son texto).
fn key_input(key: WinKey, up: bool) -> INPUT {
    let (vk, scan, unicode) = match key {
        WinKey::Vk(v) => (VIRTUAL_KEY(v), 0u16, false),
        WinKey::Unicode(u) => (VIRTUAL_KEY(0), u, true),
    };
    let base = if up { KEYEVENTF_KEYUP } else { Default::default() };
    INPUT {
        r#type: INPUT_KEYBOARD,
        Anonymous: INPUT_0 {
            ki: KEYBDINPUT {
                wVk: vk,
                wScan: scan,
                dwFlags: if unicode { base | KEYEVENTF_UNICODE } else { base },
                time: 0,
                dwExtraInfo: 0,
            },
        },
    }
}

impl Injector for WinInjector {
    fn name(&self) -> &'static str {
        "SendInput"
    }

    fn move_rel(&mut self, dx: i32, dy: i32) {
        self.send_mouse(dx, dy, 0, MOUSEEVENTF_MOVE);
    }

    fn move_abs(&mut self, nx: f32, ny: f32) {
        // nx/ny son 0..1 sobre la PANTALLA PRIMARIA; el escritorio virtual
        // puede empezar en coordenadas negativas con varios monitores.
        unsafe {
            let px = nx as f64 * GetSystemMetrics(SM_CXSCREEN) as f64;
            let py = ny as f64 * GetSystemMetrics(SM_CYSCREEN) as f64;
            let vx = GetSystemMetrics(SM_XVIRTUALSCREEN) as f64;
            let vy = GetSystemMetrics(SM_YVIRTUALSCREEN) as f64;
            let vw = GetSystemMetrics(SM_CXVIRTUALSCREEN) as f64;
            let vh = GetSystemMetrics(SM_CYVIRTUALSCREEN) as f64;
            if vw <= 0.0 || vh <= 0.0 {
                return;
            }
            let ax = (((px - vx) / vw * 65535.0).round() as i32).clamp(0, 65535);
            let ay = (((py - vy) / vh * 65535.0).round() as i32).clamp(0, 65535);
            if std::env::var_os("PEPOMOTE_DEBUG_ABS").is_some() {
                static ONCE: std::sync::Once = std::sync::Once::new();
                ONCE.call_once(|| {
                    let _ = std::fs::write(
                        "C:\\PepoMote\\abs_debug.txt",
                        format!(
                            "nx={nx} ny={ny}\nSM_CX={} SM_CY={}\nvx={vx} vy={vy} vw={vw} vh={vh}\npx={px} py={py} ax={ax} ay={ay}\n",
                            GetSystemMetrics(SM_CXSCREEN),
                            GetSystemMetrics(SM_CYSCREEN)
                        ),
                    );
                });
            }
            self.send_mouse(
                ax,
                ay,
                0,
                MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESK,
            );
        }
    }

    fn button(&mut self, btn: MouseButton, down: bool) {
        if down {
            self.activate_window_under_cursor();
        }
        let flags = match (btn, down) {
            (MouseButton::Left, true) => MOUSEEVENTF_LEFTDOWN,
            (MouseButton::Left, false) => MOUSEEVENTF_LEFTUP,
            (MouseButton::Right, true) => MOUSEEVENTF_RIGHTDOWN,
            (MouseButton::Right, false) => MOUSEEVENTF_RIGHTUP,
        };
        self.send_mouse(0, 0, 0, flags);
    }

    fn button_index(&mut self, idx: u8, down: bool) {
        match idx {
            0 => self.button(MouseButton::Left, down),
            1 => self.button(MouseButton::Right, down),
            2 => self.send_mouse(0, 0, 0, if down { MOUSEEVENTF_MIDDLEDOWN } else { MOUSEEVENTF_MIDDLEUP }),
            _ => {}
        }
    }

    /// Teclas por nombre con su CÓDIGO DE ESCANEO (KEYEVENTF_SCANCODE): los
    /// juegos (DirectInput, Raw Input) leen el escaneo, no la tecla virtual,
    /// así que WASD, Ctrl o F5 llegan como de un teclado de verdad.
    fn key_named(&mut self, name: &str, down: bool) -> bool {
        let Some((vk, extended)) = win_vk_by_name(name) else { return false };
        let scan = unsafe { MapVirtualKeyW(vk as u32, MAPVK_VK_TO_VSC) } as u16;
        if scan == 0 {
            self.send_key(VIRTUAL_KEY(vk), down);
            return true;
        }
        let mut flags = KEYEVENTF_SCANCODE;
        if extended {
            flags |= KEYEVENTF_EXTENDEDKEY;
        }
        if !down {
            flags |= KEYEVENTF_KEYUP;
        }
        let input = INPUT {
            r#type: INPUT_KEYBOARD,
            Anonymous: INPUT_0 {
                ki: KEYBDINPUT { wVk: VIRTUAL_KEY(0), wScan: scan, dwFlags: flags, time: 0, dwExtraInfo: 0 },
            },
        };
        unsafe { SendInput(&[input], std::mem::size_of::<INPUT>() as i32) };
        true
    }

    fn key(&mut self, key: KeyCode, down: bool) {
        let vk = match key {
            KeyCode::ArrowUp => VK_UP,
            KeyCode::ArrowDown => VK_DOWN,
            KeyCode::ArrowLeft => VK_LEFT,
            KeyCode::ArrowRight => VK_RIGHT,
            KeyCode::Enter => VK_RETURN,
            KeyCode::Escape => VK_ESCAPE,
            KeyCode::VolumeUp => VK_VOLUME_UP,
            KeyCode::VolumeDown => VK_VOLUME_DOWN,
            KeyCode::Mute => VK_VOLUME_MUTE,
            KeyCode::PlayPause => VK_MEDIA_PLAY_PAUSE,
            KeyCode::NextTrack => VK_MEDIA_NEXT_TRACK,
            KeyCode::PrevTrack => VK_MEDIA_PREV_TRACK,
            KeyCode::BrowserBack => VK_BROWSER_BACK,
            KeyCode::BrowserForward => VK_BROWSER_FORWARD,
            KeyCode::Backspace => VK_BACK,
            KeyCode::Space => VK_SPACE,
            KeyCode::Shift => VK_SHIFT,
            // tecla virtual del carácter en la disposición actual (byte bajo)
            KeyCode::Char(c) => VIRTUAL_KEY((unsafe { VkKeyScanW(c as u16) } & 0xFF) as u16),
        };
        self.send_key(vk, down);
    }

    /// Texto tal cual (cualquier carácter, vía KEYEVENTF_UNICODE) a la
    /// ventana con el foco, en LOTES: Windows garantiza que los eventos de
    /// una misma llamada no se intercalan con las teclas reales del usuario,
    /// y las dos unidades del par suplente de un emoji llegan juntas (sueltas,
    /// algunas aplicaciones las parten).
    fn type_text(&mut self, text: &str) -> TypeReport {
        let mut report = TypeReport::default();
        for batch in win_text_plan(text) {
            let inputs: Vec<INPUT> = batch.iter().map(|(key, up)| key_input(*key, *up)).collect();
            let sent = unsafe { SendInput(&inputs, std::mem::size_of::<INPUT>() as i32) };
            if sent as usize != inputs.len() {
                // El SO rechazó la inyección (ventana de un proceso elevado,
                // sesión bloqueada): antes el texto se evaporaba sin rastro
                report.rejected_by_os = true;
                break;
            }
        }
        report
    }

    fn wheel(&mut self, delta: i32) {
        self.send_mouse(0, 0, delta, MOUSEEVENTF_WHEEL);
    }

    fn cursor_pos(&mut self) -> Option<(f32, f32)> {
        let mut p = POINT::default();
        unsafe {
            if GetCursorPos(&mut p).is_ok() {
                let w = GetSystemMetrics(SM_CXSCREEN);
                let h = GetSystemMetrics(SM_CYSCREEN);
                if w > 0 && h > 0 {
                    return Some((p.x as f32 / w as f32, p.y as f32 / h as f32));
                }
            }
        }
        None
    }

    fn cursor_bounds(&mut self) -> Option<(f32, f32, f32, f32)> {
        // El escritorio virtual entero, en pantallas de la primaria (misma
        // normalización que cursor_pos/move_abs)
        unsafe {
            let w = GetSystemMetrics(SM_CXSCREEN) as f32;
            let h = GetSystemMetrics(SM_CYSCREEN) as f32;
            if w <= 0.0 || h <= 0.0 {
                return None;
            }
            let vx = GetSystemMetrics(SM_XVIRTUALSCREEN) as f32;
            let vy = GetSystemMetrics(SM_YVIRTUALSCREEN) as f32;
            let vw = GetSystemMetrics(SM_CXVIRTUALSCREEN) as f32;
            let vh = GetSystemMetrics(SM_CYVIRTUALSCREEN) as f32;
            if vw <= 0.0 || vh <= 0.0 {
                return None;
            }
            Some((vx / w, vy / h, (vx + vw) / w, (vy + vh) / h))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn las_teclas_virtuales_del_plan_son_las_de_windows() {
        use super::super::text_plan::{WIN_VK_BACK, WIN_VK_RETURN};
        assert_eq!(WIN_VK_RETURN, VK_RETURN.0);
        assert_eq!(WIN_VK_BACK, VK_BACK.0);
    }

    #[test]
    fn un_emoji_va_entero_en_un_solo_sendinput() {
        let lotes = win_text_plan("😀");
        assert_eq!(lotes.len(), 1);
        let inputs: Vec<INPUT> = lotes[0].iter().map(|(k, up)| key_input(*k, *up)).collect();
        assert_eq!(inputs.len(), 4, "par suplente: 2 unidades × 2 flancos");
        for (i, input) in inputs.iter().enumerate() {
            let ki = unsafe { input.Anonymous.ki };
            assert_eq!(input.r#type, INPUT_KEYBOARD);
            assert_eq!(ki.wVk, VIRTUAL_KEY(0), "texto: sin tecla virtual");
            assert!(ki.dwFlags & KEYEVENTF_UNICODE == KEYEVENTF_UNICODE);
            let soltar = i % 2 == 1;
            assert_eq!(ki.dwFlags & KEYEVENTF_KEYUP == KEYEVENTF_KEYUP, soltar);
        }
    }

    #[test]
    fn el_modo_de_activacion_se_lee_del_entorno() {
        assert_eq!(activation_mode(None), ActivationMode::Modern);
        assert_eq!(activation_mode(Some("")), ActivationMode::Modern);
        assert_eq!(activation_mode(Some("legacy")), ActivationMode::Legacy);
        assert_eq!(activation_mode(Some(" Legacy ")), ActivationMode::Legacy);
        assert_eq!(activation_mode(Some("modern")), ActivationMode::Modern);
    }
}

/// Tecla virtual de Windows (y si es «extendida») por su nombre
/// `KeyboardEvent.code`. Posiciones de un teclado QWERTY (como en la web).
fn win_vk_by_name(name: &str) -> Option<(u16, bool)> {
    let b = name.as_bytes();
    if b.len() == 4 && name.starts_with("Key") && b[3].is_ascii_uppercase() {
        return Some((b[3] as u16, false));
    }
    if b.len() == 6 && name.starts_with("Digit") && b[5].is_ascii_digit() {
        return Some((b[5] as u16, false));
    }
    if let Some(n) = name.strip_prefix('F').and_then(|n| n.parse::<u16>().ok()) {
        if (1..=24).contains(&n) {
            return Some((0x70 + n - 1, false));
        }
    }
    if let Some(n) = name.strip_prefix("Numpad").and_then(|n| n.parse::<u16>().ok()) {
        if n <= 9 {
            return Some((0x60 + n, false));
        }
    }
    Some(match name {
        "Escape" => (0x1B, false),
        "Tab" => (0x09, false),
        "CapsLock" => (0x14, false),
        "ShiftLeft" => (0xA0, false),
        "ShiftRight" => (0xA1, false),
        "ControlLeft" => (0xA2, false),
        "ControlRight" => (0xA3, true),
        "AltLeft" => (0xA4, false),
        "AltRight" => (0xA5, true),
        "MetaLeft" => (0x5B, true),
        "MetaRight" => (0x5C, true),
        "ContextMenu" => (0x5D, true),
        "Space" => (0x20, false),
        "Enter" => (0x0D, false),
        "NumpadEnter" => (0x0D, true),
        "Backspace" => (0x08, false),
        "Delete" => (0x2E, true),
        "Insert" => (0x2D, true),
        "Home" => (0x24, true),
        "End" => (0x23, true),
        "PageUp" => (0x21, true),
        "PageDown" => (0x22, true),
        "ArrowUp" => (0x26, true),
        "ArrowDown" => (0x28, true),
        "ArrowLeft" => (0x25, true),
        "ArrowRight" => (0x27, true),
        "PrintScreen" => (0x2C, true),
        "Pause" => (0x13, false),
        "Minus" => (0xBD, false),
        "Equal" => (0xBB, false),
        "BracketLeft" => (0xDB, false),
        "BracketRight" => (0xDD, false),
        "Backslash" => (0xDC, false),
        "Semicolon" => (0xBA, false),
        "Quote" => (0xDE, false),
        "Backquote" => (0xC0, false),
        "Comma" => (0xBC, false),
        "Period" => (0xBE, false),
        "Slash" => (0xBF, false),
        "IntlBackslash" => (0xE2, false),
        _ => return None,
    })
}
