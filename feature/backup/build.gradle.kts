import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget

plugins {
    id("daftar.kmp.feature")
}

kotlin {
    // The screenshot host runs in this library's own device-test APK, so its activity never ships with the app.
    extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            // The export codec, password rules and the process-scoped export controller live in core:backup.
            implementation(projects.core.backup)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.junit)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.compose.ui.test.junit4)
        }
    }
}
