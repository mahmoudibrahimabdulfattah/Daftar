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
            // Calendar dates of quote times are shown in the Egyptian zone, so the zone and instant types come from here.
            implementation(libs.kotlinx.datetime)
        }
        getByName("androidMain").dependencies {
            // The editor's system back asks before a dirty draft is dropped.
            implementation(libs.androidx.activity.compose)
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
