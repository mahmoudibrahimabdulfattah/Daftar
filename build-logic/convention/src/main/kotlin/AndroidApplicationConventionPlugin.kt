import com.android.build.api.dsl.ApplicationExtension
import com.mk.daftar.buildlogic.DaftarConfig
import com.mk.daftar.buildlogic.int
import com.mk.daftar.buildlogic.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** The Android app shell. AGP 9 compiles Kotlin itself, so no separate Kotlin plugin is applied. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<ApplicationExtension> {
            // The SDK installs API 37 as `android-37.0`; naming the minor level lets Android Studio resolve it too.
            compileSdk { version = release(libs.int("android-compileSdk")) { minorApiLevel = 0 } }
            defaultConfig {
                minSdk = libs.int("android-minSdk")
                targetSdk = libs.int("android-targetSdk")
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            buildFeatures { compose = true }
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(DaftarConfig.JVM_TOOLCHAIN)
            compilerOptions {
                jvmTarget.set(DaftarConfig.JVM_TARGET)
                allWarningsAsErrors.set(true)
            }
        }
    }
}
