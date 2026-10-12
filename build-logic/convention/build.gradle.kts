plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
    implementation(libs.spotless.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "daftar.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "daftar.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("kmpFeature") {
            id = "daftar.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
        register("androidApplication") {
            id = "daftar.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("spotless") {
            id = "daftar.spotless"
            implementationClass = "SpotlessConventionPlugin"
        }
    }
}
