import com.diffplug.gradle.spotless.SpotlessExtension
import com.mk.daftar.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** ktlint + Compose rules for every Kotlin source and build script in the repository. Apply on the root only. */
class SpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.diffplug.spotless")
        val ktlint = libs.findVersion("ktlint").get().requiredVersion
        val composeRules = libs.findVersion("composeRules").get().requiredVersion
        extensions.configure<SpotlessExtension> {
            kotlin {
                target(sources("**/*.kt"))
                ktlint(ktlint)
                    .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:$composeRules"))
                    // The design system's single theme local, and the navigator and shell locals that screens read.
                    .editorConfigOverride(
                        mapOf(
                            "compose_allowed_composition_locals" to
                                "LocalDaftarThemeInstance,LocalMirror,LocalNavigator,LocalNavigatorOrNull,LocalShellNavigation,LocalSimultaneousPanes",
                        ),
                    )
            }
            kotlinGradle {
                target(sources("**/*.kts"))
                ktlint(ktlint)
            }
        }
    }

    // A file tree (not a glob) so Gradle never walks build outputs, whose Xcode symlinks break scanning.
    private fun Project.sources(pattern: String) = fileTree(rootDir) {
        include(pattern)
        exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", "iosApp/**")
    }
}
