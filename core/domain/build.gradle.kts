plugins {
    id("daftar.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Repository contracts expose Flow and StateFlow, so consumers need the coroutines types too.
            api(projects.core.common)
            api(projects.core.model)
            api(libs.kotlinx.coroutines.core)
        }
    }
}

apply(from = rootProject.file("core/architecture-guard.gradle.kts"))
