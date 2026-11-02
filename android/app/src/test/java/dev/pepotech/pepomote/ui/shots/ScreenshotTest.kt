package dev.pepotech.pepomote.ui.shots

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.pepotech.pepomote.control.AppPrefs
import dev.pepotech.pepomote.control.ButtonState
import dev.pepotech.pepomote.control.ElementKind
import dev.pepotech.pepomote.control.GyroConfig
import dev.pepotech.pepomote.control.LookConfig
import dev.pepotech.pepomote.control.PadProfiles
import dev.pepotech.pepomote.control.RetroLayouts
import dev.pepotech.pepomote.service.LinkState
import dev.pepotech.pepomote.service.UiLink
import dev.pepotech.pepomote.ui.screens.CustomPadCanvas
import dev.pepotech.pepomote.ui.screens.ElementSettingsDialog
import dev.pepotech.pepomote.ui.screens.GamePadScreen
import dev.pepotech.pepomote.ui.screens.LocalInlineDialogs
import dev.pepotech.pepomote.ui.screens.PadMenuDialog
import dev.pepotech.pepomote.ui.screens.PadSettingsDialog
import dev.pepotech.pepomote.ui.screens.SettingsScreen
import dev.pepotech.pepomote.ui.theme.PepoColors
import dev.pepotech.pepomote.ui.theme.PepoMoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas de la interfaz para revisarla sin móvil: cada test pinta una
 * pantalla y la guarda en `build/screenshots/`. No comprueba nada (si una
 * pantalla falla al pintarse, deja el error en un .txt): son para mirarlas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-rES-w800dp-h380dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val dir = File("build/screenshots").apply { mkdirs() }

    private fun shot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        try {
            compose.setContent {
                PepoMoteTheme(dark = dark) {
                    CompositionLocalProvider(LocalInlineDialogs provides true) {
                        Box(Modifier.fillMaxSize().background(PepoColors.Background)) { content() }
                    }
                }
            }
            compose.waitForIdle()
            // La vista dibujada a mano: captureToImage espera un fotograma
            // que Robolectric no llega a pintar
            compose.runOnIdle {
                val v = compose.activity.window.decorView.rootView
                val bmp = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } catch (t: Throwable) {
            File(dir, "$name.error.txt").writeText(t.stackTraceToString())
        }
    }

    private val layout = RetroLayouts.XBOX
    private fun profile() = PadProfiles.factory(
        "Mi diseño", true, true, true,
        listOf(ButtonState.L, ButtonState.ZL, ButtonState.R, ButtonState.ZR),
        layout.center.map { it.bit }, false
    )

    @Test fun universalOriginal() {
        AppPrefs.setGyroWarnShown(compose.activity)
        shot("01_universal_original") {
            GamePadScreen(UiLink.Connected("PC", LinkState.MODE_GAMEPAD, 5f, 200f, supportsGamepad = true)) {}
        }
    }

    @Test fun lienzoPersonalizado() = shot("02_lienzo") {
        CustomPadCanvas(profile(), layout, editing = false, onElementChange = { _, _ -> }, onGear = {})
    }

    @Test fun lienzoRaton() = shot("10_lienzo_raton") {
        val p = profile().let { q ->
            q.add(dev.pepotech.pepomote.control.PadElement(ElementKind.MOUSE_PAD, 0.5f, 0.75f, 0.4f))
                .add(dev.pepotech.pepomote.control.PadElement(ElementKind.MODE_TOGGLE, 0.5f, 0.45f, 0.15f))
                .add(dev.pepotech.pepomote.control.PadElement(ElementKind.KEYBOARD, 0.38f, 0.45f, 0.13f))
        }
        androidx.compose.runtime.CompositionLocalProvider(
            dev.pepotech.pepomote.ui.screens.LocalPadActions provides dev.pepotech.pepomote.ui.screens.PadActions(mouseMode = true)
        ) {
            CustomPadCanvas(p, layout, editing = false, onElementChange = { _, _ -> }, onGear = {})
        }
    }

    @Test fun lienzoEditando() = shot("03_lienzo_editando") {
        CustomPadCanvas(profile(), layout, editing = true, onElementChange = { _, _ -> }, onGear = {})
    }

    @Test @Config(qualifiers = "es-rES-w420dp-h900dp-port-xhdpi")
    fun menuOled() {
        dev.pepotech.pepomote.ui.theme.ThemeMode.set(compose.activity, dev.pepotech.pepomote.ui.theme.ThemeMode.OLED)
        try {
            compose.setContent {
                PepoMoteTheme {
                    CompositionLocalProvider(LocalInlineDialogs provides true) {
                        Box(Modifier.fillMaxSize().background(PepoColors.Background)) {
                            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                PadMenuDialog(
                                    mouseOn = true, onMouseToggle = {}, activeName = null, locked = false, gyroOn = false,
                                    onLock = {}, onProfiles = {}, onGyroToggle = {}, onPadSettings = {}, onEdit = {}, onDismiss = {}
                                )
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.runOnIdle {
                val v = compose.activity.window.decorView.rootView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                File(dir, "11_menu_oled.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } catch (t: Throwable) {
            File(dir, "11_menu_oled.error.txt").writeText(t.stackTraceToString())
        } finally {
            dev.pepotech.pepomote.ui.theme.ThemeMode.set(compose.activity, dev.pepotech.pepomote.ui.theme.ThemeMode.SYSTEM)
        }
    }

    @Test fun lienzoClaro() = shot("04_lienzo_claro", dark = false) {
        CustomPadCanvas(profile(), layout, editing = true, onElementChange = { _, _ -> }, onGear = {})
    }

    @Test @Config(qualifiers = "es-rES-w420dp-h1500dp-port-xhdpi")
    fun ajustesDeStick() = shot("05_engranaje_stick") {
        val p = profile()
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            ElementSettingsDialog(p.elements.first { it.kind == ElementKind.STICK_R }, p, layout, {}, {}, {})
        }
    }

    @Test @Config(qualifiers = "es-rES-w420dp-h1100dp-port-xhdpi")
    fun ajustesDeBoton() = shot("06_engranaje_boton") {
        val p = profile()
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            ElementSettingsDialog(p.elements.first { it.bit == ButtonState.ZR }, p, layout, {}, {}, {})
        }
    }

    @Test @Config(qualifiers = "es-rES-w420dp-h1700dp-port-xhdpi")
    fun ajustesDelMando() = shot("07_ajustes_mando") {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            PadSettingsDialog(
                LookConfig(inertia = 0.6f), GyroConfig(enabled = true), showGyro = true, onLook = {}, onGyro = {}, onDismiss = {},
                mouse = dev.pepotech.pepomote.control.MouseConfig()
            )
        }
    }

    @Test @Config(qualifiers = "es-rES-w420dp-h4200dp-port-xhdpi")
    fun ajustes() = shot("08_ajustes") { SettingsScreen(onNewPairing = {}, onBack = {}) }

    @Test @Config(qualifiers = "es-rES-w420dp-h900dp-port-xhdpi")
    fun menuDelMando() = shot("09_menu_mando") {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            PadMenuDialog(
                mouseOn = false, onMouseToggle = {},
                activeName = "Shooter", locked = true, gyroOn = true,
                onLock = {}, onProfiles = {}, onGyroToggle = {}, onPadSettings = {}, onEdit = {}, onDismiss = {}
            )
        }
    }
}
