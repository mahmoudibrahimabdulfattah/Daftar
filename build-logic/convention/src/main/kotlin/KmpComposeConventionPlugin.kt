import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.mk.daftar.buildlogic.daftarNamespace
import com.mk.daftar.buildlogic.library
import com.mk.daftar.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** A KMP library with Compose Multiplatform UI and its own generated resources. */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("daftar.kmp.library")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<KotlinMultiplatformExtension> {
            // Compose resources ship as Android assets; the KMP Android target leaves them off by default.
            extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                androidResources { enable = true }
            }
            // The design system is built on the (experimental) Compose Styles API.
            compilerOptions.optIn.add("androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
            sourceSets.getByName("commonMain").dependencies {
                implementation(libs.library("compose-runtime"))
                implementation(libs.library("compose-foundation"))
                implementation(libs.library("compose-ui"))
                implementation(libs.library("compose-material3"))
                implementation(libs.library("compose-components-resources"))
                implementation(libs.library("compose-ui-tooling-preview"))
            }
        }
        extensions.configure<ComposeExtension> {
            extensions.configure<ResourcesExtension> {
                packageOfResClass = "$daftarNamespace.resources"
            }
        }
    }
}
