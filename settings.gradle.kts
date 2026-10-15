rootProject.name = "Daftar"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":desktopApp")
include(":core:account")
include(":core:backup")
include(":core:cloud")
include(":core:common")
include(":core:database")
include(":core:data")
include(":core:database-driver")
include(":core:navigation")
include(":core:datastore")
include(":core:designsystem")
include(":core:domain")
include(":core:model")
include(":core:network")
include(":core:security")
include(":core:ui")
include(":feature:assets")
include(":feature:banks")
include(":feature:backup")
include(":feature:auth")
include(":feature:cash")
include(":feature:cloud")
include(":feature:home")
include(":feature:investments")
include(":feature:gold")
include(":feature:journal")
include(":feature:prices")
include(":feature:settings")
include(":shared")
