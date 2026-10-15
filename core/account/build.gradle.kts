plugins {
    id("daftar.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.auth)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.googleid)
            implementation(libs.koin.android)
        }
    }
}

apply(from = rootProject.file("core/architecture-guard.gradle.kts"))
