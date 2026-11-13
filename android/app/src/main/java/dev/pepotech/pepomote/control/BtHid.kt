package dev.pepotech.pepomote.control

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * El móvil como teclado y ratón Bluetooth de verdad (perfil HID Device,
 * Android 9+). El PC, la tele o la tablet lo emparejan desde su Bluetooth
 * como cualquier ratón: no hace falta instalar nada en ellos.
 *
 * Un solo dispositivo combinado: informe 1 = teclado de arranque (modificadores
 * + 6 teclas), informe 2 = ratón (3 botones, X, Y y rueda relativos).
 *
 * Ojo: un teclado Bluetooth manda TECLAS, no letras. El texto se teclea con
 * la distribución estadounidense (QWERTY US); si el equipo tiene el teclado
 * en otra distribución, algunos signos salen distintos (las letras y números,
 * no).
 */
@SuppressLint("MissingPermission")
object BtHid {
    enum class Status { UNSUPPORTED, OFF, NO_PERMISSION, STARTING, READY, CONNECTED }

    data class State(val status: Status, val host: String? = null)

    private val _state = MutableStateFlow(State(Status.OFF))
    val state: StateFlow<State> = _state

    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var adapter: BluetoothAdapter? = null
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "bt-hid").apply { isDaemon = true } }

    // Estado de los informes
    private var buttons = 0
    private var wheelAcc = 0
    private var modifiers = 0
    private val keys = ArrayList<Int>()

    /** Informes pendientes (el texto va tecla a tecla, a ritmo del tick). */
    private val outbox = ConcurrentLinkedQueue<Pair<Int, ByteArray>>()

    private const val ID_KEYBOARD = 1
    private const val ID_MOUSE = 2
    private const val ID_CONSUMER = 3
    private const val ID_GAMEPAD = 4

    private val DESCRIPTOR = byteArrayOf(
        // Teclado (informe 1)
        0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x01,
        0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(), 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, 0x95.toByte(), 0x08, 0x81.toByte(), 0x02,
        0x95.toByte(), 0x01, 0x75, 0x08, 0x81.toByte(), 0x01,
        0x95.toByte(), 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
        0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81.toByte(), 0x00,
        0xC0.toByte(),
        // Ratón (informe 2)
        0x05, 0x01, 0x09, 0x02, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x02,
        0x09, 0x01, 0xA1.toByte(), 0x00,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
        0x95.toByte(), 0x03, 0x75, 0x01, 0x81.toByte(), 0x02,
        0x95.toByte(), 0x01, 0x75, 0x05, 0x81.toByte(), 0x01,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
        0x15, 0x81.toByte(), 0x25, 0x7F, 0x75, 0x08, 0x95.toByte(), 0x03, 0x81.toByte(), 0x06,
        0xC0.toByte(), 0xC0.toByte(),
        // Control multimedia (página Consumer): un bit por tecla
        0x05, 0x0C, 0x09, 0x01, 0xA1.toByte(), 0x01,
        0x85.toByte(), ID_CONSUMER.toByte(),
        0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x06,
        0x09, 0xCD.toByte(), // reproducir / pausar
        0x09, 0xB5.toByte(), // pista siguiente
        0x09, 0xB6.toByte(), // pista anterior
        0x09, 0xE2.toByte(), // silenciar
        0x09, 0xE9.toByte(), // subir volumen
        0x09, 0xEA.toByte(), // bajar volumen
        0x81.toByte(), 0x02,
        0x95.toByte(), 0x02, 0x81.toByte(), 0x01, // relleno hasta el byte
        0xC0.toByte(),
        // Mando de juego: 16 botones, cruceta (hat) y dos sticks
        0x05, 0x01, 0x09, 0x05, 0xA1.toByte(), 0x01,
        0x85.toByte(), ID_GAMEPAD.toByte(),
        0x05, 0x09, 0x19, 0x01, 0x29, 0x10,
        0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x10, 0x81.toByte(), 0x02,
        0x05, 0x01, 0x09, 0x39,
        0x15, 0x00, 0x25, 0x07, 0x35, 0x00, 0x46, 0x3B, 0x01, 0x65, 0x14,
        0x75, 0x04, 0x95.toByte(), 0x01, 0x81.toByte(), 0x42,
        0x65, 0x00, 0x75, 0x04, 0x95.toByte(), 0x01, 0x81.toByte(), 0x01, // relleno
        0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35, // X, Y, Z, Rz
        0x15, 0x00, 0x26, 0xFF.toByte(), 0x00, 0x35, 0x00, 0x46, 0xFF.toByte(), 0x00,
        0x75, 0x08, 0x95.toByte(), 0x04, 0x81.toByte(), 0x02,
        0xC0.toByte()
    )

    /** ¿Este móvil puede hacer de teclado y ratón Bluetooth? */
    fun supported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        return (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter != null
    }

    /** Permisos que hay que pedir antes de [start] (Android 12+). */
    val permissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE)
        } else emptyArray()

    /** Se anuncia como teclado y ratón. Idempotente. */
    fun start(context: Context) {
        if (Build.VERSION.SDK_INT < 28) {
            _state.value = State(Status.UNSUPPORTED)
            return
        }
        if (hid != null) return
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val a = manager?.adapter
        if (a == null) {
            _state.value = State(Status.UNSUPPORTED)
            return
        }
        adapter = a
        if (!a.isEnabled) {
            _state.value = State(Status.OFF)
            return
        }
        _state.value = State(Status.STARTING)
        val ok = try {
            a.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (Build.VERSION.SDK_INT < 28 || profile != BluetoothProfile.HID_DEVICE) return
                    val h = proxy as BluetoothHidDevice
                    hid = h
                    register(h)
                }

                override fun onServiceDisconnected(profile: Int) {
                    hid = null
                    host = null
                    _state.value = State(Status.OFF)
                }
            }, BluetoothProfile.HID_DEVICE)
        } catch (_: SecurityException) {
            _state.value = State(Status.NO_PERMISSION)
            return
        }
        if (!ok) _state.value = State(Status.UNSUPPORTED)
    }

    @androidx.annotation.RequiresApi(28)
    private fun register(h: BluetoothHidDevice) {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "RevolutionMote+", "Teclado y ratón", "RevolutionMote+",
            BluetoothHidDevice.SUBCLASS1_COMBO, DESCRIPTOR
        )
        try {
            val registered = h.registerApp(sdp, null, null, executor, object : BluetoothHidDevice.Callback() {
                override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                    if (registered) {
                        _state.value = State(Status.READY)
                        // Si ya estaba emparejado con este equipo, que vuelva solo
                        pluggedDevice?.let { runCatching { h.connect(it) } }
                    } else if (_state.value.status != Status.UNSUPPORTED) {
                        _state.value = State(Status.OFF)
                    }
                }

                override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            host = device
                            _state.value = State(Status.CONNECTED, runCatching { device.name }.getOrNull() ?: device.address)
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> if (host == device) {
                            host = null
                            _state.value = State(Status.READY)
                        }
                    }
                }
            })
            if (!registered) _state.value = State(Status.UNSUPPORTED)
        } catch (_: SecurityException) {
            _state.value = State(Status.NO_PERMISSION)
        }
    }

    /** Equipos ya emparejados con el móvil (para conectarse a uno con un toque). */
    fun bonded(): List<BluetoothDevice> = try {
        adapter?.bondedDevices?.toList().orEmpty()
    } catch (_: SecurityException) {
        emptyList()
    }

    /** Nombre del equipo (o su dirección si no se puede leer). */
    fun nameOf(device: BluetoothDevice): String = try {
        device.name ?: device.address
    } catch (_: SecurityException) {
        device.address
    }

    fun connect(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT < 28) return
        try {
            hid?.connect(device)
        } catch (_: SecurityException) {
        }
    }

    fun stop() {
        releaseAll()
        pump()
        if (Build.VERSION.SDK_INT < 28) return
        try {
            host?.let { hid?.disconnect(it) }
            hid?.unregisterApp()
        } catch (_: SecurityException) {
        }
        hid = null
        host = null
        _state.value = State(Status.OFF)
    }

    // --- Ratón ---

    fun move(dx: Int, dy: Int) {
        var x = dx
        var y = dy
        // Los informes van de −127 a 127: un movimiento grande se parte
        while (x != 0 || y != 0) {
            val sx = x.coerceIn(-127, 127)
            val sy = y.coerceIn(-127, 127)
            outbox += ID_MOUSE to byteArrayOf(buttons.toByte(), sx.toByte(), sy.toByte(), 0)
            x -= sx
            y -= sy
        }
    }

    fun mouseButton(index: Int, down: Boolean) {
        buttons = if (down) buttons or (1 shl index) else buttons and (1 shl index).inv()
        outbox += ID_MOUSE to byteArrayOf(buttons.toByte(), 0, 0, 0)
    }

    /** Rueda en 1/120 de muesca; aquí cuenta por muescas. */
    fun wheel(units: Int) {
        wheelAcc += units
        val notches = wheelAcc / 120
        if (notches == 0) return
        wheelAcc -= notches * 120
        outbox += ID_MOUSE to byteArrayOf(buttons.toByte(), 0, 0, notches.coerceIn(-127, 127).toByte())
    }

    // --- Teclado ---

    // --- Mando ---

    private var lastPad: ByteArray? = null

    /** El estado del mando ([BtPadReport.build]); solo sale si cambió. */
    fun gamepad(report: ByteArray) {
        if (lastPad?.contentEquals(report) == true) return
        lastPad = report
        outbox += ID_GAMEPAD to report
    }

    /** Teclas multimedia mantenidas (bits de HidKeys.consumerBit). */
    private var consumer = 0

    fun key(name: String, down: Boolean) {
        val media = HidKeys.consumerBit(name)
        if (media != 0) {
            consumer = if (down) consumer or media else consumer and media.inv()
            outbox += ID_CONSUMER to byteArrayOf(consumer.toByte())
            return
        }
        val mod = HidKeys.modifierBit(name)
        if (mod != 0) {
            modifiers = if (down) modifiers or mod else modifiers and mod.inv()
        } else {
            val code = HidKeys.usage(name) ?: return
            if (down) {
                if (code !in keys && keys.size < 6) keys += code
            } else {
                keys.remove(code)
            }
        }
        outbox += ID_KEYBOARD to keyboardReport(modifiers, keys)
    }

    /** Teclea un texto (QWERTY US), tecla a tecla. */
    fun type(text: String) {
        for (c in text) {
            val (code, shift) = HidKeys.forChar(c) ?: continue
            val mods = modifiers or (if (shift) HidKeys.SHIFT else 0)
            outbox += ID_KEYBOARD to keyboardReport(mods, listOf(code))
            outbox += ID_KEYBOARD to keyboardReport(modifiers, keys)
        }
    }

    fun releaseAll() {
        buttons = 0
        modifiers = 0
        keys.clear()
        wheelAcc = 0
        outbox += ID_MOUSE to byteArrayOf(0, 0, 0, 0)
        outbox += ID_KEYBOARD to keyboardReport(0, emptyList())
        if (consumer != 0) {
            consumer = 0
            outbox += ID_CONSUMER to byteArrayOf(0)
        }
        if (lastPad != null) {
            lastPad = null
            outbox += ID_GAMEPAD to BtPadReport.NEUTRAL
        }
    }

    private fun keyboardReport(mods: Int, codes: List<Int>): ByteArray {
        val r = ByteArray(8)
        r[0] = mods.toByte()
        for ((i, c) in codes.take(6).withIndex()) r[2 + i] = c.toByte()
        return r
    }

    /**
     * Manda lo pendiente (lo llama el tick de [Kbm], cada 8 ms). Como mucho
     * unos pocos informes de teclado por vuelta, para que el texto no se
     * coma letras en equipos lentos.
     */
    /** Quedan informes por mandar (el reloj de [Kbm] no puede dormirse). */
    val pending: Boolean get() = outbox.isNotEmpty()

    @Synchronized
    fun pump() {
        if (Build.VERSION.SDK_INT < 28) return
        val h = hid ?: run { outbox.clear(); return }
        val d = host ?: run { outbox.clear(); return }
        var keyboard = 0
        while (true) {
            val next = outbox.peek() ?: break
            if (next.first == ID_KEYBOARD && keyboard >= 2) break
            outbox.poll()
            if (next.first == ID_KEYBOARD) keyboard++
            try {
                h.sendReport(d, next.first, next.second)
            } catch (_: SecurityException) {
                outbox.clear()
                return
            }
        }
    }
}

/** Códigos de uso HID (página de teclado) por nombre `KeyboardEvent.code`. */
object HidKeys {
    const val SHIFT = 0x02

    fun modifierBit(name: String): Int = when (name) {
        "ControlLeft" -> 0x01
        "ShiftLeft" -> 0x02
        "AltLeft" -> 0x04
        "MetaLeft" -> 0x08
        "ControlRight" -> 0x10
        "ShiftRight" -> 0x20
        "AltRight" -> 0x40
        "MetaRight" -> 0x80
        else -> 0
    }

    /** Bit de una tecla multimedia en el informe Consumer (0 = no lo es). */
    fun consumerBit(name: String): Int = when (name) {
        "MediaPlayPause" -> 0x01
        "MediaTrackNext" -> 0x02
        "MediaTrackPrevious" -> 0x04
        "AudioVolumeMute" -> 0x08
        "AudioVolumeUp" -> 0x10
        "AudioVolumeDown" -> 0x20
        else -> 0
    }

    fun usage(name: String): Int? {
        if (name.length == 4 && name.startsWith("Key") && name[3] in 'A'..'Z') return 0x04 + (name[3] - 'A')
        if (name.length == 6 && name.startsWith("Digit") && name[5] in '0'..'9') {
            return if (name[5] == '0') 0x27 else 0x1E + (name[5] - '1')
        }
        if (name.startsWith("F")) name.drop(1).toIntOrNull()?.let { n -> if (n in 1..12) return 0x3A + n - 1 }
        if (name.length == 7 && name.startsWith("Numpad") && name[6] in '0'..'9') {
            return if (name[6] == '0') 0x62 else 0x59 + (name[6] - '1')
        }
        return when (name) {
            "NumLock" -> 0x53
            "NumpadDivide" -> 0x54
            "NumpadMultiply" -> 0x55
            "NumpadSubtract" -> 0x56
            "NumpadAdd" -> 0x57
            "NumpadDecimal" -> 0x63
            "Enter", "NumpadEnter" -> 0x28
            "Escape" -> 0x29
            "Backspace" -> 0x2A
            "Tab" -> 0x2B
            "Space" -> 0x2C
            "Minus" -> 0x2D
            "Equal" -> 0x2E
            "BracketLeft" -> 0x2F
            "BracketRight" -> 0x30
            "Backslash" -> 0x31
            "Semicolon" -> 0x33
            "Quote" -> 0x34
            "Backquote" -> 0x35
            "Comma" -> 0x36
            "Period" -> 0x37
            "Slash" -> 0x38
            "CapsLock" -> 0x39
            "PrintScreen" -> 0x46
            "Pause" -> 0x48
            "Insert" -> 0x49
            "Home" -> 0x4A
            "PageUp" -> 0x4B
            "Delete" -> 0x4C
            "End" -> 0x4D
            "PageDown" -> 0x4E
            "ArrowRight" -> 0x4F
            "ArrowLeft" -> 0x50
            "ArrowDown" -> 0x51
            "ArrowUp" -> 0x52
            "IntlBackslash" -> 0x64
            "ContextMenu" -> 0x65
            else -> null
        }
    }

    /** Carácter → (uso HID, con mayúsculas) en QWERTY US; null si no tiene tecla. */
    fun forChar(c: Char): Pair<Int, Boolean>? = when (c) {
        in 'a'..'z' -> (0x04 + (c - 'a')) to false
        in 'A'..'Z' -> (0x04 + (c - 'A')) to true
        in '1'..'9' -> (0x1E + (c - '1')) to false
        '0' -> 0x27 to false
        ' ' -> 0x2C to false
        '\n' -> 0x28 to false
        '\t' -> 0x2B to false
        '\b' -> 0x2A to false
        '-' -> 0x2D to false
        '_' -> 0x2D to true
        '=' -> 0x2E to false
        '+' -> 0x2E to true
        '[' -> 0x2F to false
        '{' -> 0x2F to true
        ']' -> 0x30 to false
        '}' -> 0x30 to true
        '\\' -> 0x31 to false
        '|' -> 0x31 to true
        ';' -> 0x33 to false
        ':' -> 0x33 to true
        '\'' -> 0x34 to false
        '"' -> 0x34 to true
        '`' -> 0x35 to false
        '~' -> 0x35 to true
        ',' -> 0x36 to false
        '<' -> 0x36 to true
        '.' -> 0x37 to false
        '>' -> 0x37 to true
        '/' -> 0x38 to false
        '?' -> 0x38 to true
        '!' -> 0x1E to true
        '@' -> 0x1F to true
        '#' -> 0x20 to true
        '$' -> 0x21 to true
        '%' -> 0x22 to true
        '^' -> 0x23 to true
        '&' -> 0x24 to true
        '*' -> 0x25 to true
        '(' -> 0x26 to true
        ')' -> 0x27 to true
        else -> null
    }
}
