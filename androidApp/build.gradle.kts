import java.util.Properties

plugins {
    id("daftar.android.application")
}

// Firebase is optional. The Google Services and Crashlytics plugins run only when the ignored, variant-specific config
// file is present; without it the app builds, runs everything local, and reports cloud backups as unavailable.
if (file("google-services.json").exists()) {
    pluginManager.apply("com.google.gms.google-services")
    pluginManager.apply("com.google.firebase.crashlytics")
    // A variant whose package is not registered in the file (e.g. release before its app exists in Firebase) builds
    // without Firebase, and the app then reports cloud backups as unavailable.
    val registered = file("google-services.json").readText()
    val releaseRegistered = "\"package_name\": \"com.mk.daftar\"" in registered
    tasks.matching { it.name == "processReleaseGoogleServices" }.configureEach {
        enabled = releaseRegistered
    }
    // Mapping files reach Crashlytics from release builds only. Debug builds never upload, and an unregistered release
    // package has nowhere to upload to. The mapping-id injection always runs: Crashlytics refuses to start without it.
    tasks.matching { it.name.startsWith("uploadCrashlyticsMappingFile") }
        .configureEach {
            enabled = releaseRegistered && name.endsWith("Release")
        }
}

// The upload key stays out of git. keystore.properties (storeFile, storePassword, keyAlias, keyPassword) sits next to
// settings.gradle.kts; keystore.properties.example lists the keys. Without the file the release build is left
// unsigned, and it never falls back to the debug key.
val keystore: Properties? = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
    namespace = "com.mk.daftar.android"
    defaultConfig {
        applicationId = "com.mk.daftar"
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Crash reports and analytics are on in release only, so emulator and debug sessions never reach the data.
        manifestPlaceholders["crashlyticsCollectionEnabled"] = "true"
        manifestPlaceholders["analyticsCollectionEnabled"] = "true"
    }
    signingConfigs {
        if (keystore != null) {
            create("upload") {
                // A partial file fails the build here; it never silently produces an unsigned release.
                storeFile = rootProject.file(
                    requireNotNull(keystore.getProperty("storeFile")) { "keystore.properties: storeFile is missing" },
                )
                storePassword = requireNotNull(keystore.getProperty("storePassword")) {
                    "keystore.properties: storePassword is missing"
                }
                keyAlias =
                    requireNotNull(keystore.getProperty("keyAlias")) { "keystore.properties: keyAlias is missing" }
                keyPassword = requireNotNull(keystore.getProperty("keyPassword")) {
                    "keystore.properties: keyPassword is missing"
                }
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "false"
            manifestPlaceholders["analyticsCollectionEnabled"] = "false"
        }
        release {
            // Stated explicitly: a release build is never debuggable, whatever the shell defaults are.
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
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
    implementation(libs.androidx.credentials)
    implementation(project.dependencies.platform(libs.firebase.bom))
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.crashlytics)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(projects.core.account)
    implementation(projects.core.cloud)
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
