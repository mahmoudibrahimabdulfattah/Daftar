import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget

plugins {
    id("daftar.kmp.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
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
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite)
        }
        // Room integration tests run on the Android device, the iOS simulator and the desktop JVM. Both compile the same tests from
        // databaseTest/, and both open the database through core:database-driver's SQLCipher driver.
        getByName("androidDeviceTest") {
            kotlin.srcDir("src/databaseTest/kotlin")
            dependencies {
                implementation(projects.core.databaseDriver)
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.junit)
            }
        }
        matching { it.name == "iosTest" }.configureEach {
            kotlin.srcDir("src/databaseTest/kotlin")
            dependencies {
                implementation(projects.core.databaseDriver)
            }
        }
        // Desktop runs the same integration tests on the JVM, over the desktop SQLCipher-format driver.
        getByName("desktopTest") {
            kotlin.srcDir("src/databaseTest/kotlin")
            dependencies {
                implementation(projects.core.databaseDriver)
            }
        }
        getByName("desktopMain").dependencies {
            implementation(projects.core.common)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64", "kspDesktop").forEach {
        add(it, libs.androidx.room.compiler)
    }
}

apply(from = rootProject.file("core/architecture-guard.gradle.kts"))
