//! El logo de RevolutionMote+ (el mismo que el icono de la app), en RGBA.

/// El logo a 128 px, RGBA sin comprimir (sale de `assets/revolutionmote-icon.svg`).
const LOGO_128: &[u8] = include_bytes!("logo128.rgba");
const LOGO_SIZE: u32 = 128;

/// RGBA size×size del logo, reescalado con un promedio por área (nítido a 16–64 px).
pub fn logo_rgba(size: u32) -> Vec<u8> {
    let mut out = Vec::with_capacity((size * size * 4) as usize);
    let k = LOGO_SIZE as f32 / size as f32;
    for y in 0..size {
        for x in 0..size {
            let (x0, y0) = ((x as f32 * k) as u32, (y as f32 * k) as u32);
            let (x1, y1) = ((((x + 1) as f32 * k).ceil() as u32).min(LOGO_SIZE), (((y + 1) as f32 * k).ceil() as u32).min(LOGO_SIZE));
            let mut acc = [0u32; 4];
            let mut n = 0u32;
            for sy in y0..y1.max(y0 + 1) {
                for sx in x0..x1.max(x0 + 1) {
                    let i = ((sy.min(LOGO_SIZE - 1) * LOGO_SIZE + sx.min(LOGO_SIZE - 1)) * 4) as usize;
                    for c in 0..4 {
                        acc[c] += LOGO_128[i + c] as u32;
                    }
                    n += 1;
                }
            }
            for c in acc {
                out.push((c / n.max(1)) as u8);
            }
        }
    }
    out
}

/// El logo con un punto verde abajo a la derecha (bandeja: hay móviles
/// conectados). Un aro transparente de 1 px lo separa del logo.
#[cfg_attr(not(any(windows, target_os = "macos")), allow(dead_code))]
pub fn logo_rgba_badge(size: u32) -> Vec<u8> {
    let mut out = logo_rgba(size);
    let s = size as f32;
    let (bx, by, br) = (s * 0.78, s * 0.78, s * 0.19);
    let green = [0x7B, 0xC9, 0x4C, 255];
    for y in 0..size {
        for x in 0..size {
            let dx = x as f32 + 0.5 - bx;
            let dy = y as f32 + 0.5 - by;
            let d = (dx * dx + dy * dy).sqrt();
            let i = ((y * size + x) * 4) as usize;
            if d < br {
                out[i..i + 4].copy_from_slice(&green);
            } else if d < br + 1.0 {
                out[i..i + 4].copy_from_slice(&[0, 0, 0, 0]);
            }
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn px(buf: &[u8], size: u32, x: u32, y: u32) -> [u8; 4] {
        let i = ((y * size + x) * 4) as usize;
        [buf[i], buf[i + 1], buf[i + 2], buf[i + 3]]
    }

    #[test]
    fn el_logo_tiene_el_tamano_pedido_y_es_opaco_en_el_centro() {
        let s = 32;
        let logo = logo_rgba(s);
        assert_eq!(logo.len(), (s * s * 4) as usize);
        assert_eq!(px(&logo, s, 16, 16)[3], 255, "centro opaco");
    }

    #[test]
    fn el_badge_es_verde_y_recorta_el_aro_a_su_alrededor() {
        let s = 32;
        let plain = logo_rgba(s);
        let badge = logo_rgba_badge(s);
        assert_eq!(px(&badge, s, 25, 25), [0x7B, 0xC9, 0x4C, 255], "centro del badge");
        // los píxeles del aro transparente del badge que en el logo eran azules
        let (bx, by, br) = (s as f32 * 0.78, s as f32 * 0.78, s as f32 * 0.19);
        let mut checked = 0;
        for y in 0..s {
            for x in 0..s {
                let d = ((x as f32 + 0.5 - bx).powi(2) + (y as f32 + 0.5 - by).powi(2)).sqrt();
                if (br..br + 1.0).contains(&d) && px(&plain, s, x, y)[3] == 255 {
                    assert_eq!(px(&badge, s, x, y)[3], 0, "({x},{y}) debe quedar transparente");
                    checked += 1;
                }
            }
        }
        assert!(checked > 0, "el aro del badge cruza el aro del logo");
        // lejos del badge, el logo sigue igual
        assert_eq!(px(&badge, s, 16, 16), px(&plain, s, 16, 16));
        assert_eq!(px(&badge, s, 16, 2), px(&plain, s, 16, 2));
    }
}
