pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tablechips"

include(":core")
include(":server")

// The Android client needs an SDK installation. Everything that matters for the
// rules engine lives in :core and :server, which are plain JVM modules, so a
// checkout without an SDK can still run `./gradlew build` on them.
val androidSdkAvailable =
    System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

if (androidSdkAvailable) {
    include(":app")
} else {
    logger.lifecycle("No Android SDK found (ANDROID_HOME / local.properties): skipping :app.")
}
