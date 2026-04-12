plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.atomicfu) apply false
}

allprojects {
    group = "io.tts.sdk"
    version = System.getenv("VERSION") ?: "1.0.0"

    repositories {
        mavenCentral()
        google()
    }
}
