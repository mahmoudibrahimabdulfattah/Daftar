plugins {
    id("daftar.kmp.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Snapshot values are domain and model types; the password facade is the only crypto this module uses.
            api(projects.core.common)
            api(projects.core.model)
            api(projects.core.domain)
            api(projects.core.security)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        // The Argon2id and XChaCha20 round trips run on the host with the desktop libsodium build, as in core:security.
        getByName("androidHostTest").dependencies {
            implementation(libs.libsodium.bindings.jvm)
        }
    }
}

configurations.matching { it.name.startsWith("androidHostTest") }.configureEach {
    exclude(group = "com.ionspin.kotlin", module = "multiplatform-crypto-libsodium-bindings")
}

apply(from = rootProject.file("core/architecture-guard.gradle.kts"))
