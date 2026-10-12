import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget

plugins {
    id("daftar.kmp.library")
}

kotlin {
    // Device tests open the real SQLCipher database through Room, on the emulator or a phone.
    extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(projects.core.database)
            implementation(projects.core.databaseDriver)
            implementation(projects.core.datastore)
            implementation(projects.core.network)
            implementation(projects.core.security)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        // Real Room and SQLCipher integration tests run on the Android device and on the iOS simulator from the same
        // sources, so the repositories are exercised through the production driver and builders.
        getByName("androidDeviceTest") {
            kotlin.srcDir("src/storageTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.junit)
            }
        }
        matching { it.name == "iosTest" }.configureEach {
            kotlin.srcDir("src/storageTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
