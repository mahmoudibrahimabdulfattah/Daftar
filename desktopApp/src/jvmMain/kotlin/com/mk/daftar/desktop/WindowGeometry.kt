package com.mk.daftar.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.mk.daftar.core.common.desktop.DesktopAppDirectories
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.io.File
import java.util.Properties

/**
 * The window's last normal size, position and maximized state, kept in a small non-sensitive file in the app's private
 * directory. A minimized window is never restored minimized, and bounds that no longer fit a connected screen are
 * replaced by the centered default.
 */
internal object WindowGeometry {
    private val file: File get() = DesktopAppDirectories.root.resolve("window.properties")

    fun restore(): WindowState {
        val saved = runCatching { Properties().apply { file.inputStream().use { load(it) } } }.getOrNull()
        val width = saved?.getProperty("width")?.toIntOrNull()
        val height = saved?.getProperty("height")?.toIntOrNull()
        val x = saved?.getProperty("x")?.toIntOrNull()
        val y = saved?.getProperty("y")?.toIntOrNull()
        val maximized = saved?.getProperty("maximized") == "true"
        val placement = if (maximized) WindowPlacement.Maximized else WindowPlacement.Floating
        if (width == null || height == null || x == null || y == null || !fitsAScreen(x, y, width, height)) {
            return WindowState(
                placement = placement,
                position = WindowPosition.PlatformDefault,
                size = DpSize(DEFAULT_WIDTH, DEFAULT_HEIGHT),
            ).also { it.position = WindowPosition(androidx.compose.ui.Alignment.Center) }
        }
        return WindowState(
            placement = placement,
            position = WindowPosition(x.dp, y.dp),
            size = DpSize(width.coerceAtLeast(MIN_WIDTH_DP).dp, height.coerceAtLeast(MIN_HEIGHT_DP).dp),
        )
    }

    fun save(state: WindowState) {
        val position = state.position
        if (state.isMinimized || !position.isSpecified) return
        val properties = Properties().apply {
            setProperty("width", state.size.width.value.toInt().toString())
            setProperty("height", state.size.height.value.toInt().toString())
            setProperty("x", position.x.value.toInt().toString())
            setProperty("y", position.y.value.toInt().toString())
            setProperty("maximized", (state.placement == WindowPlacement.Maximized).toString())
        }
        runCatching { file.outputStream().use { properties.store(it, null) } }
    }

    /** Whether the window's title area would be on a connected screen's usable area. */
    private fun fitsAScreen(x: Int, y: Int, width: Int, height: Int): Boolean {
        val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        if (GraphicsEnvironment.isHeadless()) return false
        val titleArea = Rectangle(x, y, width, TITLE_AREA_HEIGHT)
        return environment.screenDevices.any { device ->
            device.defaultConfiguration.bounds.intersection(titleArea).width >= MIN_VISIBLE_WIDTH
        }
    }

    private const val TITLE_AREA_HEIGHT = 32
    private const val MIN_VISIBLE_WIDTH = 160
}
