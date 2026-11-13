package dev.pepotech.pepomote.control

import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * El informe HID del mando Bluetooth (7 bytes): 16 botones, la cruceta como
 * «hat» y los dos sticks. Los botones van en el orden que Android y Linux
 * leen como mando estándar (1 = A, 2 = B, 4 = X, 5 = Y, 7 = LB, 8 = RB,
 * 9 = LT, 10 = RT, 11 = Select, 12 = Start, 13 = Home, 14 = L3, 15 = R3).
 */
object BtPadReport {
    const val HAT_NONE = 8

    private val BUTTONS = listOf(
        ButtonState.A to 0, ButtonState.B to 1, ButtonState.X to 3, ButtonState.Y to 4,
        ButtonState.L to 6, ButtonState.R to 7, ButtonState.ZL to 8, ButtonState.ZR to 9,
        ButtonState.MINUS to 10, ButtonState.PLUS to 11, ButtonState.HOME to 12,
        ButtonState.STICK_L to 13, ButtonState.STICK_R to 14
    )

    // Después de BUTTONS: build() lo usa al inicializar el objeto
    val NEUTRAL: ByteArray = build(0, 0, 0, 0, 0)

    /** Cruceta → hat: 0 arriba, en el sentido del reloj hasta 7; [HAT_NONE] suelta. */
    fun hat(mask: Int): Int {
        val up = mask and ButtonState.DPAD_UP != 0
        val down = mask and ButtonState.DPAD_DOWN != 0
        val left = mask and ButtonState.DPAD_LEFT != 0
        val right = mask and ButtonState.DPAD_RIGHT != 0
        val v = (if (up) 1 else 0) - (if (down) 1 else 0)
        val h = (if (right) 1 else 0) - (if (left) 1 else 0)
        return when {
            v > 0 && h == 0 -> 0
            v > 0 && h > 0 -> 1
            v == 0 && h > 0 -> 2
            v < 0 && h > 0 -> 3
            v < 0 && h == 0 -> 4
            v < 0 && h < 0 -> 5
            v == 0 && h < 0 -> 6
            v > 0 && h < 0 -> 7
            else -> HAT_NONE
        }
    }

    /** Eje −127..127 → 0..255 con el centro en 128. */
    fun axis(v: Int): Int = (v.coerceIn(-127, 127) + 128)

    /**
     * [mask]: los botones de [ButtonState]; sticks en −127..127 con +y arriba
     * (en HID, +y es abajo: se invierte aquí).
     */
    fun build(mask: Int, lx: Int, ly: Int, rx: Int, ry: Int): ByteArray {
        var buttons = 0
        for ((bit, index) in BUTTONS) if (mask and bit != 0) buttons = buttons or (1 shl index)
        return byteArrayOf(
            (buttons and 0xFF).toByte(),
            (buttons shr 8 and 0xFF).toByte(),
            hat(mask).toByte(),
            axis(lx).toByte(),
            axis(-ly).toByte(),
            axis(rx).toByte(),
            axis(-ry).toByte()
        )
    }
}

/**
 * Mando universal por Bluetooth, sin receptor: el móvil se presenta como un
 * mando de juego de verdad (y como teclado y ratón, para las teclas y el
 * touchpad del diseño). Lo que se toca va a [ButtonState] como siempre; un
 * reloj lo lee y lo manda por [BtHid].
 */
object BtPad {
    /** Cada cuánto se mira el mando (ms): por debajo del intervalo de Bluetooth. */
    private const val TICK_MS = 8L

    private val _active = MutableStateFlow(false)

    /** Se pidió el mando por Bluetooth (la pantalla del mando lo abre en ese modo). */
    val active: StateFlow<Boolean> = _active

    private var exec: ScheduledExecutorService? = null

    /** Se entra al modo: aún sin tocar el Bluetooth (primero, los permisos). */
    fun enter() {
        _active.value = true
    }

    /** Con los permisos dados: anunciarse y empezar a mandar. */
    @Synchronized
    fun start(context: Context) {
        if (!_active.value || exec != null) return
        BtHid.start(context)
        Kbm.backend = Kbm.Backend.BT
        exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "bt-pad").apply { isDaemon = true } }.also {
            it.scheduleAtFixedRate({
                try {
                    BtHid.gamepad(
                        BtPadReport.build(
                            ButtonState.current(), ButtonState.stickX(), ButtonState.stickY(),
                            ButtonState.stickRX(), ButtonState.stickRY()
                        )
                    )
                    BtHid.pump()
                } catch (_: Exception) {
                }
            }, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS)
        }
    }

    @Synchronized
    fun stop() {
        val wasRunning = exec != null
        exec?.shutdownNow()
        exec = null
        if (wasRunning) {
            Kbm.backend = Kbm.Backend.NONE
            BtHid.stop()
        }
        _active.value = false
    }
}
