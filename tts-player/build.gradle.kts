plugins {
    kotlin("jvm")
    `maven-publish`
}

dependencies {
    api(project(":tts-core"))
    testImplementation(project(":tts-testing"))

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.assertj:assertj-core:3.25.1")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "tts-player"
            version = project.version.toString()
        }
    }
}
