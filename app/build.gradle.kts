plugins {
    // Android Gradle plugin 9 brings its own Kotlin support: the
    // kotlin-android plugin must not be applied here any more.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/*
  The version lives in version.txt at the root, and only there: release-please
  bumps it from the commit messages when it cuts a release (see README,
  "Releasing"). The version code is derived from it, so the two can never
  disagree: MAJOR·10000 + MINOR·100 + PATCH, which keeps it rising as long as
  minor and patch stay under a hundred.
*/
val appVersion = rootProject.file("version.txt").readText().trim()
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { (major, minor, patch) ->
    require(minor < 100 && patch < 100) { "version.txt: $appVersion does not fit the version code scheme" }
    (major * 10000 + minor * 100 + patch).coerceAtLeast(1)
}

/*
  Release builds are signed only when a keystore is handed over through the
  environment: the release workflow decodes one from the repository's secrets.
  Anywhere else — a contributor's machine, F-Droid's build server, which signs
  with its own key — a release build comes out unsigned, and that is fine.
*/
val releaseKeystore: String? = System.getenv("TABLECHIPS_KEYSTORE")

android {
    namespace = "io.github.tablechips.app"
    compileSdk = 36

    defaultConfig {
        // The only irreversible decision of the project: see README.
        applicationId = "io.github.tablechips"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("TABLECHIPS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TABLECHIPS_KEY_ALIAS")
                keyPassword = System.getenv("TABLECHIPS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            // AGP writes the git commit into the APK, or an error when it finds
            // no repository: the one byte-level difference between two builds
            // of the same source. Leaving it out is what makes the build
            // reproducible, so F-Droid can check its build against ours.
            vcsInfo {
                include = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // Ship only the languages that are actually translated, so the app does
    // not carry hundreds of locale folders from the libraries.
    androidResources {
        localeFilters += listOf("en", "ca", "es")
    }

    buildFeatures {
        compose = true
    }

    // The screens are rendered on the JVM in unit tests, which needs the
    // resources: no emulator is available, and an app nobody has run is worse
    // than one whose screens have at least been drawn once.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // The dependency block AGP writes into the APK is encrypted with Google's
    // key: nobody else can read it, and F-Droid rejects APKs that carry it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(project(":server"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Reading a code off somebody else's screen. CameraX and zxing only: no
    // Play Services anywhere near this, or the app could not be on F-Droid.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // No component library on purpose: the design system is drawn from
    // foundation primitives, so Material never enters the build.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
