plugins {
    id("daftar.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The cloud contracts are the domain's; the export codec and the password-crypto facade come from core:backup.
            api(projects.core.domain)
            implementation(projects.core.backup)
            implementation(projects.core.security)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            // Firestore only: no Storage, Functions, Analytics or Crashlytics. App Check is installed by the app shell.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.firestore)
            implementation(libs.koin.android)
        }
    }
}

apply(from = rootProject.file("core/architecture-guard.gradle.kts"))
