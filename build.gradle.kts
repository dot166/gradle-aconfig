plugins {
    `kotlin-dsl`
    `maven-publish`
    id("com.vanniktech.maven.publish") version "0.36.0"
}

gradlePlugin {
    // Define the plugin
    val aconfig by plugins.creating {
        id = "io.github.dot166.aconfig"
        implementationClass = "io.github.dot166.aconfig.GradleAconfigPlugin"
    }
}

group = "io.github.dot166"
version = providers.exec {
    commandLine("cat", "ver")
}.standardOutput.asText.get().trim()

mavenPublishing {
    coordinates(group.toString(), "aconfig", version.toString())

    pom {
        name = "aconfig"
        description = "gradle aconfig parser"
        inceptionYear = "2025"
        url = "https://github.com/dot166/gradle-aconfig"
        licenses {
            license {
                name.set("MIT License")
                url.set("https://choosealicense.com/licenses/mit/")
            }
        }
        developers {
            developer {
                id = "dot166"
                name = "._______166"
                url = "https://dot166.github.io"
            }
        }
        scm {
            url = "https://github.com/github.com/dot166/gradle-aconfig"
            connection = "scm:git:git://github.com/github.com/dot166/gradle-aconfig.git"
            developerConnection = "scm:git:ssh://git@github.com/github.com/dot166/gradle-aconfig.git"
        }
    }
}

dependencies {
    implementation("com.android.tools.build:gradle:8.13.2")
    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.1")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    // Use JUnit Jupiter for unit tests.
    useJUnitPlatform()
}