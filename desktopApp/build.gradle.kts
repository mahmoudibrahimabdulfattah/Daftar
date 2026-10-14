import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

/*
 * The desktop shell: one window around the shared app. A build is a debug build unless `-Pdaftar.desktop.release=true`
 * is passed, and the flag is compiled in, so a packaged release can never select debug-only key handling at run time.
 */
val desktopRelease = providers.gradleProperty("daftar.desktop.release").map { it.toBoolean() }.orElse(false)
val desktopVersion = "1.0.0"

val generateDesktopBuildFlavor = tasks.register("generateDesktopBuildFlavor") {
    val release = desktopRelease
    val version = desktopVersion
    val output = layout.buildDirectory.dir("generated/desktopBuildFlavor")
    inputs.property("release", release)
    inputs.property("version", version)
    outputs.dir(output)
    doLast {
        val file = output.get().file("com/mk/daftar/desktop/DesktopBuildFlavor.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.mk.daftar.desktop
            |
            |/** Generated at build time. A release build is compiled with [DEBUG] false. */
            |internal object DesktopBuildFlavor {
            |    const val DEBUG: Boolean = ${!release.get()}
            |    const val VERSION: String = "$version"
            |}
            |
            """.trimMargin(),
        )
    }
}

kotlin {
    jvmToolchain(21)
    jvm()
    sourceSets {
        jvmMain {
            kotlin.srcDir(generateDesktopBuildFlavor)
            dependencies {
                implementation(projects.shared)
                implementation(projects.core.common)
                implementation(projects.core.data)
                implementation(projects.core.security)
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.jna)
                implementation(libs.jna.platform)
            }
        }
        // Offscreen screenshots of the real app at the design's window sizes, driven through the UI.
        jvmTest.dependencies {
            implementation(compose.desktop.uiTestJUnit4)
            implementation(libs.kotlin.test)
        }
    }
}

// Screens are written here; the task never runs unless asked for by name.
val screensRequested = gradle.startParameter.taskNames.any { it.contains("desktopApp:jvmTest") }
tasks.withType<Test>().configureEach {
    systemProperty("daftar.screens.dir", layout.buildDirectory.dir("screens").get().asFile.absolutePath)
    val requested = screensRequested
    onlyIf { requested }
}

compose.desktop {
    application {
        mainClass = "com.mk.daftar.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "Daftar"
            packageVersion = desktopVersion
            vendor = "Daftar"
            macOS {
                bundleID = "com.mk.daftar.desktop"
            }
            windows {
                menuGroup = "Daftar"
                perUserInstall = true
                upgradeUuid = "6f1c2a7e-3b4d-4e8a-9c51-0d2e7f8a9b13"
            }
        }
    }
}
