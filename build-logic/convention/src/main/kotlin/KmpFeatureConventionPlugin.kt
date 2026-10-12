import com.mk.daftar.buildlogic.library
import com.mk.daftar.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A screen-level feature: Compose UI, ViewModels, Navigation 3 entries registered in Koin, on
 * top of the core modules. Features never depend on each other; they navigate by route keys.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("daftar.kmp.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").dependencies {
                implementation(project(":core:model"))
                implementation(project(":core:domain"))
                implementation(project(":core:designsystem"))
                implementation(project(":core:ui"))
                implementation(project(":core:navigation"))
                implementation(project.dependencies.platform(libs.library("koin-bom")))
                implementation(libs.library("koin-compose-viewmodel"))
                implementation(libs.library("androidx-lifecycle-viewmodel-compose"))
                implementation(libs.library("androidx-lifecycle-runtime-compose"))
                implementation(libs.library("androidx-navigation3-ui"))
                implementation(libs.library("androidx-lifecycle-viewmodel-navigation3"))
                implementation(libs.library("koin-compose-navigation3"))
                implementation(libs.library("kotlinx-serialization-json"))
            }
        }
    }
}
