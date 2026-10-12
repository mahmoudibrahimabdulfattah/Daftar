plugins {
    id("daftar.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            implementation(libs.bignum)
        }
    }
}

apply(from = rootProject.file("core/architecture-guard.gradle.kts"))
