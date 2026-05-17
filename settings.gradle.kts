rootProject.name = "hopec"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":codegen")
include(":core")
include(":driver")
include(":typecheck")
include(":renamer")
include(":parser")
include(":desugarer")
