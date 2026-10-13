import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.mk.daftar.buildlogic.DaftarConfig
import com.mk.daftar.buildlogic.daftarNamespace
import com.mk.daftar.buildlogic.int
import com.mk.daftar.buildlogic.library
import com.mk.daftar.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** A Kotlin Multiplatform library for Android and iOS (device + Apple Silicon simulator). */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(DaftarConfig.JVM_TOOLCHAIN)
            compilerOptions {
                allWarningsAsErrors.set(true)
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }

            extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                namespace = daftarNamespace
                // The SDK installs API 37 as `android-37.0`; naming the minor level lets Android Studio resolve it too.
                compileSdk { version = release(libs.int("android-compileSdk")) { minorApiLevel = 0 } }
                minSdk = libs.int("android-minSdk")
                compilerOptions { jvmTarget.set(DaftarConfig.JVM_TARGET) }
                withHostTest { }
            }

            iosArm64()
            iosSimulatorArm64()

            sourceSets.getByName("commonMain").dependencies {
                implementation(libs.library("kotlinx-coroutines-core"))
            }
            sourceSets.getByName("commonTest").dependencies {
                implementation(libs.library("kotlin-test"))
                implementation(libs.library("kotlinx-coroutines-test"))
            }
        }
    }
}
