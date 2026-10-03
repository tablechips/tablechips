plugins {
    // Android Gradle plugin 9 brings its own Kotlin support: the
    // kotlin-android plugin must not be applied here any more.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.tablechips.app"
    compileSdk = 36

    defaultConfig {
        // The only irreversible decision of the project: see README.
        applicationId = "io.github.tablechips"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
