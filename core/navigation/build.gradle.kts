plugins {
    id("daftar.kmp.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Keys are Navigation 3 NavKeys, and the navigator is observed by NavDisplay through snapshot state.
            api(libs.androidx.navigation3.ui)
            implementation(libs.compose.runtime)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

apply(from = rootProject.file("core/architecture-guard.gradle.kts"))
