plugins {
    id("daftar.android.application")
}

android {
    namespace = "com.mk.daftar.android"
    defaultConfig {
        applicationId = "com.mk.daftar"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // Stated explicitly: a release build is never debuggable, whatever the shell defaults are.
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.shared)
    // The theme callback names the domain's ThemeMode, so the shell needs the domain module on its own classpath.
    implementation(projects.core.domain)
    // The build variant selects the Android key policy; the policy type lives in core:security.
    implementation(projects.core.security)
    implementation(libs.androidx.activity.compose)
    // The activity is an AppCompat activity, so the in-app language choice applies on every API level.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.process)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)

    androidTestImplementation(projects.core.domain)
    androidTestImplementation(projects.core.data)
    androidTestImplementation(projects.core.database)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(libs.kotlin.test)
}
