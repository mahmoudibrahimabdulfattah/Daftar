plugins {
    id("daftar.android.application")
}

android {
    namespace = "com.mk.daftar.android"
    defaultConfig {
        applicationId = "com.mk.daftar"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
}
