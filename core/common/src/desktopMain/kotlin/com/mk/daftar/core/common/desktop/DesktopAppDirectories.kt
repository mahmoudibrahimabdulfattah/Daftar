package com.mk.daftar.core.common.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/** The desktop operating system Daftar runs on. Anything else is treated as a development host. */
enum class DesktopOs {
    MAC,
    WINDOWS,
    OTHER,
    ;

    companion object {
        val current: DesktopOs by lazy {
            val name = System.getProperty("os.name").orEmpty().lowercase()
            when {
                name.startsWith("mac") -> MAC
                name.startsWith("windows") -> WINDOWS
                else -> OTHER
            }
        }
    }
}

/**
 * The private, device-only directory of the desktop app, chosen once by the launcher before anything reads it.
 * macOS: `~/Library/Application Support/<name>`, excluded from Time Machine. Windows: `%LOCALAPPDATA%\<name>`, which is
 * never roamed. Debug and release builds use different names, so their files never meet. The directory is created
 * owner-only where the file system supports it.
 */
object DesktopAppDirectories {
    @Volatile
    private var configured: File? = null

    /** Selects the directory for [appName]. Called once by the launcher; a second, different call is refused. */
    fun configure(appName: String) = configureAt(baseDirectory().resolve(appName))

    /** Selects [directory] itself, for test harnesses that run the app in a throwaway directory. */
    @Synchronized
    fun configureAt(directory: File) {
        val chosen = directory
        val current = configured
        check(current == null || current == chosen) { "The app directory is already configured" }
        if (current == null) {
            ensurePrivate(chosen)
            // Children inherit the exclusion, so only the root is marked.
            if (DesktopOs.current == DesktopOs.MAC) excludeFromTimeMachine(chosen)
            configured = chosen
        }
    }

    /** The app directory. Throws if the launcher has not configured it. */
    val root: File
        get() = checkNotNull(configured) { "The app directory is not configured" }

    /** A private subdirectory of [root], created on first use. */
    fun directory(name: String): File = root.resolve(name).also { ensurePrivate(it) }

    private fun baseDirectory(): File {
        val home = File(System.getProperty("user.home"))
        return when (DesktopOs.current) {
            DesktopOs.MAC -> home.resolve("Library/Application Support")
            DesktopOs.WINDOWS -> System.getenv("LOCALAPPDATA")?.let(::File) ?: home.resolve("AppData/Local")
            DesktopOs.OTHER -> System.getenv("XDG_DATA_HOME")?.let(::File) ?: home.resolve(".local/share")
        }
    }

    private fun ensurePrivate(directory: File) {
        if (!directory.exists()) directory.mkdirs()
        check(directory.isDirectory) { "The app directory cannot be created" }
        if (DesktopOs.current != DesktopOs.WINDOWS) {
            Files.setPosixFilePermissions(directory.toPath(), PosixFilePermissions.fromString("rwx------"))
        }
    }

    /** The sticky exclusion `tmutil` itself sets. Best effort: a failure leaves the directory usable. */
    private fun excludeFromTimeMachine(directory: File) {
        try {
            ProcessBuilder("/usr/bin/tmutil", "addexclusion", directory.absolutePath)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor()
        } catch (ignored: Exception) {
            // Time Machine may be absent; the directory stays private either way.
        }
    }
}
