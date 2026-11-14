//! Apuntado por giro: el movimiento del móvil mueve el stick derecho.
//!
//! Un mando de Xbox no tiene giroscopio, así que la única forma de que el
//! giro llegue a un juego cualquiera es por el stick. Cuanto más deprisa
//! gira el móvil, más lejos del centro se va el stick — igual que hace
//! Steam Input con los mandos de Switch.
//!
//! El sesgo del giroscopio se mata con la **zona muerta suave** del motor de
//! puntero, que es la respuesta que ya tiene este proyecto para el giro sin
//! referencia absoluta (el fallback h1 de `pointer::engine`): un sesgo
//! pequeño sin cancelar dejaría el stick fuera del centro para siempre y la
//! cámara giraría sola. Por eso aquí no hay estado ni estimador: función
//! pura, mismo umbral, mismos signos.

use crate::pointer::{soft_deadzone, GYRO_DEADZONE_RADS, SIGN_X, SIGN_Y};
use pmp::InputPacket;

/// Velocidad de giro que lleva el stick al tope, en grados por segundo.
/// A menos, más sensible. 220 °/s es un giro de muñeca rápido pero cómodo.
pub const FULL_SCALE_DEG_S: f32 = 220.0;

const FULL: i16 = 32767;

/// El stick derecho que pide el giro de este paquete, o el centro si el
/// móvil está quieto (o no tiene giroscopio de verdad).
pub fn stick(p: &InputPacket, full_scale_deg_s: f32) -> (i16, i16) {
    // `FLAG_TILT` es un móvil sin giroscopio real apuntando por inclinación:
    // su `gyro` no dice nada y moverlo sería ruido.
    if p.flags & pmp::FLAG_TILT != 0 {
        return (0, 0);
    }
    let full = full_scale_deg_s.max(1.0).to_radians();

    // Los mismos ejes y signos que el fallback relativo del puntero: el
    // horizontal sale de `gyro[2]` y el vertical de `gyro[0]`. Con la
    // gravedad del paquete, medidos en el mundo (ver `world`): con la muñeca
    // girada, mover a izquierda y derecha sigue siendo solo horizontal.
    let (h, v) = world(p.gyro, p.accel).unwrap_or((p.gyro[2], p.gyro[0]));
    let gz = soft_deadzone(h, GYRO_DEADZONE_RADS);
    let gx = soft_deadzone(v, GYRO_DEADZONE_RADS);

    // En pantalla la Y crece hacia abajo; en el stick, hacia arriba. De ahí
    // el signo cambiado en el vertical y no en el horizontal.
    let x = SIGN_X * gz / full;
    let y = -SIGN_Y * gx / full;
    (clamp(x), clamp(y))
}

/// (horizontal, vertical) del giro en el marco del mundo, como el mando de
/// Wii: horizontal = alrededor de la vertical real (el acelerómetro en reposo
/// apunta hacia arriba), vertical = alrededor del eje x del móvil sin su parte
/// vertical. Con el móvil tumbado y recto da lo mismo que los ejes del móvil
/// (`gyro[2]`, `gyro[0]`). `None` si el acelerómetro no es la gravedad (una
/// sacudida, un móvil sin él) o si el eje x está casi vertical: ahí se vuelve
/// a los ejes del móvil.
fn world(gyro: [f32; 3], accel: [f32; 3]) -> Option<(f32, f32)> {
    let n = (accel[0] * accel[0] + accel[1] * accel[1] + accel[2] * accel[2]).sqrt();
    if !n.is_finite() || !(6.0..=14.0).contains(&n) {
        return None;
    }
    let u = [accel[0] / n, accel[1] / n, accel[2] / n];
    let r = [1.0 - u[0] * u[0], -u[0] * u[1], -u[0] * u[2]];
    let rn = (r[0] * r[0] + r[1] * r[1] + r[2] * r[2]).sqrt();
    if rn < 0.25 {
        return None;
    }
    let yaw = gyro[0] * u[0] + gyro[1] * u[1] + gyro[2] * u[2];
    let pitch = (gyro[0] * r[0] + gyro[1] * r[1] + gyro[2] * r[2]) / rn;
    Some((yaw, pitch))
}

fn clamp(v: f32) -> i16 {
    if !v.is_finite() {
        return 0;
    }
    (v.clamp(-1.0, 1.0) * f32::from(FULL)).round() as i16
}

#[cfg(test)]
mod tests {
    use super::*;

    fn con_gyro(gx: f32, gz: f32) -> InputPacket {
        let mut p = InputPacket::default();
        p.gyro = [gx, 0.0, gz];
        p
    }

    #[test]
    fn el_movil_quieto_deja_el_stick_en_el_centro() {
        assert_eq!(stick(&con_gyro(0.0, 0.0), FULL_SCALE_DEG_S), (0, 0));
    }

    #[test]
    fn el_sesgo_pequeno_del_giroscopio_no_mueve_nada() {
        // Un MEMS barato en reposo deriva bastante menos que la zona muerta
        // (0,03 rad/s ≈ 1,7 °/s). Si esto se rompe, la cámara gira sola.
        let casi = GYRO_DEADZONE_RADS * 0.9;
        assert_eq!(stick(&con_gyro(casi, casi), FULL_SCALE_DEG_S), (0, 0));
        assert_eq!(stick(&con_gyro(-casi, -casi), FULL_SCALE_DEG_S), (0, 0));
    }

    #[test]
    fn a_la_velocidad_de_tope_el_stick_llega_al_maximo() {
        let tope = FULL_SCALE_DEG_S.to_radians() + GYRO_DEADZONE_RADS;
        let (x, _) = stick(&con_gyro(0.0, tope), FULL_SCALE_DEG_S);
        assert_eq!(x.abs(), FULL);
        let (_, y) = stick(&con_gyro(tope, 0.0), FULL_SCALE_DEG_S);
        assert_eq!(y.abs(), FULL);
    }

    #[test]
    fn pasarse_de_vueltas_no_desborda() {
        let (x, y) = stick(&con_gyro(500.0, -500.0), FULL_SCALE_DEG_S);
        assert_eq!((x.abs(), y.abs()), (FULL, FULL), "se recorta, no da la vuelta");
    }

    #[test]
    fn los_ejes_van_como_el_puntero() {
        // Mismo convenio que el fallback relativo de `pointer::engine`: el
        // horizontal sale de gyro[2] y el vertical de gyro[0], con la Y del
        // stick al revés que la de la pantalla.
        let medio = FULL_SCALE_DEG_S.to_radians() / 2.0 + GYRO_DEADZONE_RADS;
        let (x, y) = stick(&con_gyro(0.0, medio), FULL_SCALE_DEG_S);
        assert_eq!(y, 0, "gyro[2] no toca el vertical");
        assert_eq!(x.signum(), SIGN_X.signum() as i16);

        let (x, y) = stick(&con_gyro(medio, 0.0), FULL_SCALE_DEG_S);
        assert_eq!(x, 0, "gyro[0] no toca el horizontal");
        assert_eq!(y.signum(), -SIGN_Y.signum() as i16);
    }

    #[test]
    fn mas_sensibilidad_es_menos_grados_por_segundo() {
        let g = 1.0_f32;
        let (lento, _) = stick(&con_gyro(0.0, g), 400.0);
        let (rapido, _) = stick(&con_gyro(0.0, g), 100.0);
        assert!(rapido.abs() > lento.abs(), "{rapido} vs {lento}");
    }

    #[test]
    fn un_movil_sin_giroscopio_no_apunta() {
        let mut p = con_gyro(5.0, 5.0);
        p.flags |= pmp::FLAG_TILT;
        assert_eq!(stick(&p, FULL_SCALE_DEG_S), (0, 0));
    }

    #[test]
    fn con_la_muneca_girada_mover_en_horizontal_no_sube_ni_baja() {
        // Tumbado (la gravedad en +z) da lo mismo que los ejes del móvil
        assert_eq!(world([0.3, 0.0, 0.7], [0.0, 0.0, 9.8]), Some((0.7, 0.3)));
        // Girado 40° sobre el eje de apuntado (y): la vertical queda entre x y z
        let (s, c) = 40f32.to_radians().sin_cos();
        let accel = [9.8 * s, 0.0, 9.8 * c];
        // Girar alrededor de la vertical real: todo horizontal
        let (h, v) = world([s, 0.0, c], accel).unwrap();
        assert!((h - 1.0).abs() < 1e-4 && v.abs() < 1e-4, "({h}, {v})");
        let mut p = InputPacket::default();
        p.gyro = [s, 0.0, c];
        p.accel = accel;
        let (_, y) = stick(&p, FULL_SCALE_DEG_S);
        assert_eq!(y, 0, "el giro horizontal no se cuela en el vertical");
        // Una sacudida (el acelerómetro no es la gravedad): los ejes del móvil
        assert_eq!(world([1.0, 0.0, 0.0], [0.0, 0.0, 30.0]), None);
    }

    #[test]
    fn un_gyro_roto_no_tumba_nada() {
        assert_eq!(stick(&con_gyro(f32::NAN, f32::INFINITY), FULL_SCALE_DEG_S), (0, 0));
        let (x, y) = stick(&con_gyro(1.0, 1.0), 0.0);
        assert_eq!((x.abs(), y.abs()), (FULL, FULL), "sensibilidad cero no divide por cero");
    }
}
