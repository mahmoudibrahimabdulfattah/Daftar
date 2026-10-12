package com.mk.daftar.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

internal object DaftarConfig {
    val JVM_TARGET = JvmTarget.JVM_17
    const val JVM_TOOLCHAIN = 21
    const val BASE_PACKAGE = "com.mk.daftar"
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

internal fun VersionCatalog.int(alias: String): Int = findVersion(alias).get().requiredVersion.toInt()

/** `:core:model` → `com.mk.daftar.core.model`, so every module gets a unique Android namespace. */
internal val Project.daftarNamespace: String
    get() = DaftarConfig.BASE_PACKAGE + path.replace(':', '.').replace('-', '_')
