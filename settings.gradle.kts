pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "tts"
include(":tts-core", ":tts-player", ":tts-testing")
