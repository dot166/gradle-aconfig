/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Gradle plugin project to get you started.
 * For more details on writing Custom Plugins, please refer to https://docs.gradle.org/8.13/userguide/custom_plugins.html in the Gradle documentation.
 * This project uses @Incubating APIs which are subject to change.
 */

plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`
    `maven-publish`
    id("com.vanniktech.maven.publish") version "0.31.0"
}

gradlePlugin {
    // Define the plugin
    val aconfig by plugins.creating {
        id = "io.github.dot166.aconfig"
        implementationClass = "io.github.dot166.aconfig.GradleAconfigPlugin"
    }
}

group = "io.github.dot166"
version = "1.0.1"

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
    implementation("com.android.tools.build:gradle:8.9.0")
    implementation(gradleKotlinDsl())
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.2.0.202503040940-r")
    implementation("commons-io:commons-io:2.18.0")
    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    // Use JUnit Jupiter for unit tests.
    useJUnitPlatform()
}

