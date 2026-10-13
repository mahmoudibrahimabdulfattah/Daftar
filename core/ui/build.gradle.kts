plugins {
    id("daftar.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Screens and the shell draw with the design system's tokens and components.
            api(projects.core.designsystem)
            // Formatting and allocation read exact amounts from the model; no floating-point conversion lives here.
            implementation(projects.core.model)
            // The shell's navigator is read by the root reselect scroll only.
            implementation(projects.core.navigation)
            // The pinned price refresh action reads the shared refresh controller from the app's graph.
            implementation(projects.core.domain)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.compose)
            api(libs.androidx.lifecycle.viewmodel.compose)
            api(libs.androidx.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

apply(from = rootProject.file("core/architecture-guard.gradle.kts"))
