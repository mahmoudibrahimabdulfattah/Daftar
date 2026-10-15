import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget

plugins {
    id("daftar.kmp.library")
}

/*
 * The macOS key helper (Secure Enclave and Touch ID), compiled from src/desktopNative/macos for both Mac architectures
 * and bundled where JNA looks for it. Other hosts skip it; their builds report the enclave as unavailable.
 */
val daftarMacSource = file("src/desktopNative/macos/DaftarMac.swift")
val daftarMacResources: File = layout.buildDirectory.dir("daftarmac/resources").get().asFile
val buildDaftarMacHelper = tasks.register<Exec>("buildDaftarMacHelper") {
    val source = daftarMacSource
    val out = daftarMacResources
    val work = layout.buildDirectory.dir("daftarmac/work").get().asFile
    onlyIf { System.getProperty("os.name").startsWith("Mac") }
    inputs.file(source)
    outputs.dir(out)
    doFirst {
        work.mkdirs()
        out.resolve("darwin-aarch64").mkdirs()
        out.resolve("darwin-x86-64").mkdirs()
    }
    commandLine(
        "/bin/bash",
        "-c",
        "set -e; export SDKROOT=\"\$(/usr/bin/xcrun --sdk macosx --show-sdk-path)\"; " +
            "for arch in arm64 x86_64; do /usr/bin/xcrun swiftc -O -emit-library -module-name DaftarMac " +
            "-target \$arch-apple-macos12 " +
            "-o '${work.absolutePath}'/libdaftarmac-\$arch.dylib '${source.absolutePath}'; done; " +
            "/usr/bin/lipo -create -output '${out.absolutePath}/darwin-aarch64/libdaftarmac.dylib' " +
            "'${work.absolutePath}'/libdaftarmac-arm64.dylib '${work.absolutePath}'/libdaftarmac-x86_64.dylib; " +
            "cp '${out.absolutePath}/darwin-aarch64/libdaftarmac.dylib' '${out.absolutePath}/darwin-x86-64/libdaftarmac.dylib'",
    )
}

kotlin {
    // Device-side tests run the real Android Keystore and the bundled libsodium on an emulator or phone. Host tests
    // stay in commonTest.
    extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        androidMain.dependencies {
            // BiometricManager reports strong-biometric capability, and BiometricPrompt needs the same crypto type.
            implementation(libs.androidx.biometric)
        }
        commonMain.dependencies {
            api(projects.core.common)
            implementation(libs.libsodium.bindings)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        // The Android AAR carries libsodium only for Android ABIs. Host tests need the desktop JVM build of the same
        // bindings, so the host classpath swaps the AAR for the JVM artifact.
        getByName("androidHostTest").dependencies {
            implementation(libs.libsodium.bindings.jvm)
        }
        getByName("desktopMain") {
            resources.srcDir(buildDaftarMacHelper.map { daftarMacResources })
            dependencies {
                implementation(libs.jna)
            }
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.junit)
        }
    }
}

configurations.matching { it.name.startsWith("androidHostTest") }.configureEach {
    exclude(group = "com.ionspin.kotlin", module = "multiplatform-crypto-libsodium-bindings")
}

apply(from = rootProject.file("core/architecture-guard.gradle.kts"))
