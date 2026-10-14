plugins {
    id("daftar.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Entry validation reads the numeric normalizer; the list and detail use the list-detail scene.
            implementation(projects.core.common)
            implementation(libs.compose.material3.adaptive.navigation3)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
