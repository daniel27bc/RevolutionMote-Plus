//! Audio PCM del móvil hacia la entrada virtual de VB-CABLE en Windows.

use super::Sessions;
use crate::pairing::PairingInfo;
use crate::state::{LockTolerant, Role};
use rodio::buffer::SamplesBuffer;
use rodio::cpal::traits::HostTrait;
use rodio::{Device, DeviceTrait, OutputStream, Sink};
use serde_json::{json, Value};
use std::io::{BufReader, Read, Write};
use std::net::{IpAddr, TcpStream};
use std::sync::{mpsc, Mutex, OnceLock};
use std::time::Duration;

const SAMPLE_RATE: u32 = 48_000;
const FRAME_BYTES: usize = 960 * 2;
static AUDIO: Mutex<()> = Mutex::new(());
static WORKER: OnceLock<Option<mpsc::SyncSender<AudioJob>>> = OnceLock::new();

struct AudioJob {
    frames: mpsc::Receiver<Vec<i16>>,
    reply: mpsc::SyncSender<&'static str>,
}

fn worker() -> Option<&'static mpsc::SyncSender<AudioJob>> {
    WORKER.get_or_init(|| {
        let (sender, jobs) = mpsc::sync_channel::<AudioJob>(1);
        std::thread::Builder::new().name("pmp-microphone".into()).spawn(move || {
            // CPAL conserva un enumerador COM global. Su primer hilo tiene
            // que seguir vivo incluso entre dos conexiones del micrófono.
            while let Ok(job) = jobs.recv() {
                let reply = job.reply.clone();
                if std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| play(job))).is_err() {
                    let _ = reply.try_send("unavailable");
                }
            }
        }).ok().map(|_| sender)
    }).as_ref()
}

fn play(job: AudioJob) {
    let (_stream, sink) = match open_output() {
        Ok(output) => output,
        Err(error) => { let _ = job.reply.send(error); return; }
    };
    if job.reply.send("ready").is_err() {
        return;
    }
    while let Ok(frame) = job.frames.recv_timeout(Duration::from_secs(3)) {
        if sink.len() < 4 {
            sink.append(SamplesBuffer::new(1, SAMPLE_RATE, frame));
        }
    }
    sink.stop();
}

pub(super) fn handle(
    mut reader: BufReader<TcpStream>,
    mut writer: TcpStream,
    hello: &Value,
    sessions: &Sessions,
    pairing: &PairingInfo,
    peer: IpAddr,
) {
    let session = hello["session_id"].as_u64().and_then(|id| u32::try_from(id).ok());
    let authorized = || session.is_some_and(|id| {
        sessions.lock_tolerant().get(&id).is_some_and(|s| s.peer == peer && s.role == Role::Wiimote)
    });
    if hello["pv"].as_u64() != Some(1) || hello["token"].as_str() != Some(pairing.token.as_str()) || !authorized() {
        let _ = reply(&mut writer, "unauthorized");
        return;
    }
    let Ok(_audio) = AUDIO.try_lock() else {
        let _ = reply(&mut writer, "busy");
        return;
    };
    let Some(worker) = worker() else {
        let _ = reply(&mut writer, "unavailable");
        return;
    };
    let (frames, receiver) = mpsc::sync_channel(2);
    let (result, status) = mpsc::sync_channel(1);
    if worker.try_send(AudioJob { frames: receiver, reply: result }).is_err() {
        let _ = reply(&mut writer, "busy");
        return;
    }
    let status = status.recv_timeout(Duration::from_secs(5)).unwrap_or("unavailable");
    let _ = reader.get_ref().set_read_timeout(Some(Duration::from_secs(2)));
    if !authorized() || !reply(&mut writer, status) || status != "ready" {
        return;
    }
    let mut frame = [0u8; FRAME_BYTES];
    while authorized() && reader.read_exact(&mut frame).is_ok() {
        // Cada bloque dura 20 ms. Si la salida se retrasa, se pierde el
        // bloque nuevo y se mantiene acotada la latencia del micrófono.
        if let Err(mpsc::TrySendError::Disconnected(_)) = frames.try_send(decode(&frame)) {
            break;
        }
    }
}

fn reply(writer: &mut TcpStream, status: &str) -> bool {
    writeln!(writer, "{}", json!({"m":"microphone","status":status})).is_ok()
}

fn open_output() -> Result<(OutputStream, Sink), &'static str> {
    let mut devices = rodio::cpal::default_host().output_devices().map_err(|_| "unavailable")?;
    let device = devices.find(|device| device.name().is_ok_and(|name| is_cable_input(&name)))
        .ok_or("driver_missing")?;
    output_for(&device)
}

fn is_cable_input(name: &str) -> bool {
    let name = name.to_ascii_lowercase();
    name.starts_with("cable input") && name.contains("vb-audio")
}

fn output_for(device: &Device) -> Result<(OutputStream, Sink), &'static str> {
    let (stream, handle) = OutputStream::try_from_device(device).map_err(|_| "unavailable")?;
    let sink = Sink::try_new(&handle).map_err(|_| "unavailable")?;
    Ok((stream, sink))
}

fn decode(frame: &[u8]) -> Vec<i16> {
    frame.chunks_exact(2).map(|sample| i16::from_le_bytes([sample[0], sample[1]])).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn el_audio_va_solo_a_la_entrada_virtual() {
        assert!(is_cable_input("CABLE Input (VB-Audio Virtual Cable)"));
        assert!(!is_cable_input("CABLE Output (VB-Audio Virtual Cable)"));
        assert!(!is_cable_input("Altavoces (Realtek)"));
    }

    #[test]
    fn decodifica_pcm_con_signo_en_orden_little_endian() {
        assert_eq!(decode(&[0x34, 0x12, 0x00, 0x80, 0xff, 0xff]), [0x1234, i16::MIN, -1]);
    }
}
