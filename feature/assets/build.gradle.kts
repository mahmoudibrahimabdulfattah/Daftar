plugins {
    id("daftar.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The bank list and its detail use the Material list-detail scene strategy from this library.
            implementation(libs.compose.material3.adaptive.navigation3)
        }
    }
}
