package com.mk.daftar.desktop

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.mk.daftar.App
import com.mk.daftar.core.common.desktop.DesktopAppDirectories
import com.mk.daftar.core.security.DesktopSecurityConfiguration
import com.mk.daftar.core.security.desktopSecurityConfigurationModule
import com.mk.daftar.di.DesktopBuildConfiguration
import com.mk.daftar.di.desktopBuildConfigurationModule
import com.mk.daftar.di.initKoin
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import org.koin.core.context.GlobalContext

/**
 * Renders the real desktop app offscreen in a throwaway directory with the debug key policy, drives it through the
 * UI with synthetic input only, and writes PNGs to `build/screens`. Run by name: `./gradlew :desktopApp:jvmTest`.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopScreens {
    private val out = File(System.getProperty("daftar.screens.dir")).apply { mkdirs() }

    @Test
    fun firstRunAndShell() {
        start()
        screen(1200, 800) {
            capture("01-create-1200x800")
            enterPin("246810")
            tap("متابعة")
            capture("02-confirm-1200x800")
            enterPin("246810")
            tap("تأكيد")
            waitForIdle()
            Thread.sleep(1500)
            waitForIdle()
            capture("03-biometric-step-1200x800")
            if (onAllNodes(hasText("لاحقًا") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) tap("لاحقًا")
            Thread.sleep(2500)
            waitForIdle()
            capture("03-home-1200x800")
        }
        for ((width, height) in listOf(720 to 600, 1024 to 768, 1440 to 900)) {
            screen(width, height) {
                Thread.sleep(800)
                waitForIdle()
                capture("04-home-${width}x$height")
                if (width == 1024) {
                    // Keyboard order starts at the rail (design phase 28).
                    onRoot().performKeyInput { pressKey(Key.Tab) }
                    waitForIdle()
                    onNode(isFocused()).assert(hasText("الرئيسية"))
                    capture("04-tab-${width}x$height")
                }
                for ((label, name) in listOf("الأصول" to "assets", "السجل" to "journal", "الإعدادات" to "settings")) {
                    tap(label)
                    Thread.sleep(500)
                    waitForIdle()
                    capture("05-$name-${width}x$height")
                }
                // A list beside its (placeholder) detail where the window allows two panes.
                tap("الأصول")
                tap("البنوك")
                Thread.sleep(500)
                waitForIdle()
                capture("06-banks-${width}x$height")
                // Hover: the pointer over a rail item fills it softly.
                key("الإعدادات").performMouseInput { moveTo(center) }
                waitForIdle()
                Thread.sleep(400)
                capture("07-hover-${width}x$height")
            }
        }
    }

    private fun start() {
        if (GlobalContext.getOrNull() != null) return
        DesktopAppDirectories.configureAt(Files.createTempDirectory("daftar-screens").toFile())
        initKoin {
            modules(
                desktopSecurityConfigurationModule(DesktopSecurityConfiguration.AllowSoftwareForDebug),
                desktopBuildConfigurationModule(DesktopBuildConfiguration("1.0.0", "1", debug = true)),
            )
        }
    }

    private fun screen(width: Int, height: Int, block: DesktopComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width, height) {
            setContent { App(showsDebugBanner = false, developerTools = false, desktopLayout = true) }
            waitForIdle()
            Thread.sleep(700)
            waitForIdle()
            block()
        }

    private fun DesktopComposeUiTest.enterPin(pin: String) {
        pin.forEach { digit ->
            key(digit.toString()).performClick()
            waitForIdle()
        }
    }

    private fun DesktopComposeUiTest.key(text: String): SemanticsNodeInteraction =
        onAllNodes(hasText(text) and hasClickAction(), useUnmergedTree = false)[0]

    private fun DesktopComposeUiTest.tap(text: String) {
        key(text).performClick()
        waitForIdle()
    }

    private fun DesktopComposeUiTest.capture(name: String) {
        val image = onRoot().captureToImage().toAwtImage()
        ImageIO.write(image, "png", File(out, "$name.png"))
    }
}
