plugins {
    id("daftar.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.backup)
            implementation(projects.core.account)
            implementation(projects.core.cloud)
            implementation(projects.feature.cloud)
            implementation(projects.core.designsystem)
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.datastore)
            implementation(projects.core.network)
            implementation(projects.core.security)
            implementation(projects.core.navigation)
            implementation(projects.core.ui)
            implementation(projects.feature.auth)
            implementation(projects.feature.cash)
            implementation(projects.feature.home)
            implementation(projects.feature.assets)
            implementation(projects.feature.gold)

            implementation(projects.feature.banks)
            implementation(projects.feature.backup)
            implementation(projects.feature.journal)
            implementation(projects.feature.prices)
            implementation(projects.feature.settings)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.navigation3)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)
            implementation(libs.compose.material3.adaptive.navigation3)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            // The graph probes name the database builder and preferences types, so their signatures must resolve.
            implementation(projects.core.database)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.androidx.biometric)
            implementation(libs.koin.android)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.appcompat)
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
}
