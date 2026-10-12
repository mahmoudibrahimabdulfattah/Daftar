import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget

plugins {
    id("daftar.kmp.library")
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
