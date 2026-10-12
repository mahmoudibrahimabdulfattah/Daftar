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
            // Entry validation reads the numeric normalizer; the cash feature never reaches core:data.
            implementation(projects.core.common)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.junit)
            implementation(libs.androidx.activity.compose)
        }
    }
}
