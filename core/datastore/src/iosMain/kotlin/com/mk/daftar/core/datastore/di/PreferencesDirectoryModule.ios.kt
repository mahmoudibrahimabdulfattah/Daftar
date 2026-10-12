@file:OptIn(ExperimentalForeignApi::class)

package com.mk.daftar.core.datastore.di

import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * Application Support, excluded from backup, with file protection until the first unlock after boot. The file is
 * device-only: backups and device transfers leave it out, so a restored device starts with the defaults.
 */
internal actual val preferencesDirectoryModule: Module = module {
    single(PreferencesDirectory) { deviceOnlyPreferencesDirectory() }
}

private fun deviceOnlyPreferencesDirectory(): String {
    val fileManager = NSFileManager.defaultManager
    val support = fileManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path ?: error("Application Support is unavailable")
    val directory = "$support/Daftar/preferences"
    fileManager.createDirectoryAtPath(
        directory,
        withIntermediateDirectories = true,
        attributes = mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
        error = null,
    )
    NSURL.fileURLWithPath(directory).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return directory
}
