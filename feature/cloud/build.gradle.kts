plugins {
    id("daftar.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Sign-in, versions and deletion come through the domain contract only. Restore and upload entry points
            // are typed routes and callbacks owned by the shell, so this feature never reaches core:data or another feature.
            implementation(projects.core.backup)
            implementation(projects.core.common)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
