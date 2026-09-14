plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Golden rule of the build: the rules engine is plain Kotlin with no Android
// dependency, so `./gradlew :core:test` runs without an emulator.
//
// Bytecode targets 17 because the Android client consumes this module; the JDK
// that runs the build only needs to be 17 or newer, and no toolchain is
// downloaded (F-Droid builds offline).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}

tasks.test {
    testLogging {
        events("failed")
    }
}
