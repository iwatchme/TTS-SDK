plugins {
    id("com.android.library") version "8.2.2" apply false
    kotlin("android") version "1.9.22" apply false
    kotlin("jvm") version "1.9.22" apply false
}

allprojects {
    group = "io.tts.sdk"
    version = System.getenv("VERSION") ?: "1.0.0"

    repositories {
        mavenCentral()
        google()
    }
}

subprojects {
    plugins.withId("maven-publish") {
        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitLab"
                    val projectId = System.getenv("CI_PROJECT_ID") ?: ""
                    url = uri("https://gitlab.com/api/v4/projects/$projectId/packages/maven")
                    credentials(HttpHeaderCredentials::class) {
                        name = "Job-Token"
                        value = System.getenv("CI_JOB_TOKEN") ?: ""
                    }
                    authentication {
                        create<HttpHeaderAuthentication>("header")
                    }
                }
            }
        }
    }
}
