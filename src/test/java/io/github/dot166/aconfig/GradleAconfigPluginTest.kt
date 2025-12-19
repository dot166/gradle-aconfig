package io.github.dot166.aconfig

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/**
 * A simple unit test for the 'io.github.dot166.aconfig' plugin.
 */
internal class GradleAconfigPluginTest {
    @Test
    @Throws(Exception::class)
    fun testPluginWithAGPApplication() {
        // Setup the test build
        val projectDir = File("build/test/agp-application")
        deleteDirectory(projectDir)
        Files.createDirectories(projectDir.toPath())
        val manifestDir = File(projectDir.absolutePath + "/src/main")
        Files.createDirectories(manifestDir.toPath())
        writeString(
            File(projectDir, "settings.gradle.kts"), """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        google()
                        mavenCentral()
                    }
                }
                
                 dependencyResolutionManagement {
                     repositories {
                         google()
                         mavenCentral()
                     }
                 }
                 """.trimIndent()
        )
        writeString(
            File(projectDir, "build.gradle.kts"), """
                plugins {
                    id("com.android.application")
                    id("io.github.dot166.aconfig")
                }
                
                android {
                    namespace = "io.github.dot166.aconfig.test"
                    compileSdk = 36
                    defaultConfig {
                        minSdk = 29
                    }
                }
                
                aconfig {
                    aconfigFiles = mutableListOf("jLib.aconfig", "settingstheme.aconfig")
                    textProtoRepo = "https://github.com/dot166/platform_build_release"
                }
                """.trimIndent()
        )
        writeString(
            File(projectDir, "local.properties"),
            "sdk.dir=" + File(System.getProperty("user.home")).absolutePath + "/Android/Sdk"
        )
        writeString(
            File(manifestDir, "AndroidManifest.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest/>
                
                """.trimIndent()
        )

        // Run the build
        var command =
            "curl -O https://raw.githubusercontent.com/dot166/jOS_j-lib/refs/tags/v4.4.2/aconfig/jLib.aconfig"
        var processBuilder =
            ProcessBuilder(*command.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())
        processBuilder.directory(projectDir)
        var process = processBuilder.start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw RuntimeException(toString_ReadAllBytes(process.errorStream))
        }
        command =
            "curl -O https://raw.githubusercontent.com/dot166/platform_frameworks_base/refs/heads/16-qpr1/packages/SettingsLib/SettingsTheme/aconfig/settingstheme.aconfig"
        processBuilder =
            ProcessBuilder(*command.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())
        processBuilder.directory(projectDir)
        process = processBuilder.start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw RuntimeException(toString_ReadAllBytes(process.errorStream))
        }
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("assembleDebug")
        runner.withProjectDir(projectDir)
        runner.build()
    }

    @Test
    @Throws(Exception::class)
    fun testPluginWithAGPLibrary() {
        // Setup the test build
        val projectDir = File("build/test/agp-library")
        deleteDirectory(projectDir)
        Files.createDirectories(projectDir.toPath())
        writeString(
            File(projectDir, "settings.gradle.kts"), """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        google()
                        mavenCentral()
                    }
                }
                
                dependencyResolutionManagement {
                    repositories {
                        google()
                        mavenCentral()
                    }
                }
                """.trimIndent()
        )
        writeString(
            File(projectDir, "build.gradle.kts"), """
                plugins {
                    id("com.android.library")
                    id("io.github.dot166.aconfig")
                }
                
                android {
                    namespace = "io.github.dot166.aconfig.test"
                    compileSdk = 36
                    defaultConfig {
                        minSdk = 29
                    }
                }
                
                aconfig {
                    aconfigFiles = mutableListOf("jLib.aconfig", "settingstheme.aconfig")
                    textProtoRepo = "https://github.com/dot166/platform_build_release"
                }
                """.trimIndent()
        )
        writeString(
            File(projectDir, "local.properties"),
            "sdk.dir=" + File(System.getProperty("user.home")).absolutePath + "/Android/Sdk"
        )

        // Run the build
        var command =
            "curl -O https://raw.githubusercontent.com/dot166/jOS_j-lib/refs/tags/v4.4.2/aconfig/jLib.aconfig"
        var processBuilder =
            ProcessBuilder(*command.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())
        processBuilder.directory(projectDir)
        var process = processBuilder.start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw RuntimeException(toString_ReadAllBytes(process.errorStream))
        }
        command =
            "curl -O https://raw.githubusercontent.com/dot166/platform_frameworks_base/refs/heads/16-qpr1/packages/SettingsLib/SettingsTheme/aconfig/settingstheme.aconfig"
        processBuilder =
            ProcessBuilder(*command.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())
        processBuilder.directory(projectDir)
        process = processBuilder.start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw RuntimeException(toString_ReadAllBytes(process.errorStream))
        }
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("assembleDebug")
        runner.withProjectDir(projectDir)
        runner.build()
    }

    @Test
    @Throws(Exception::class)
    fun testPluginWithOpenJDKJava() {
        // Setup the test build
        val projectDir = File("build/test/java")
        deleteDirectory(projectDir)
        Files.createDirectories(projectDir.toPath())
        writeString(File(projectDir, "settings.gradle.kts"), "")
        writeString(
            File(projectDir, "build.gradle.kts"), """
                plugins {
                    `java`
                    id("io.github.dot166.aconfig")
                }
                
                aconfig {
                    aconfigFiles = mutableListOf("jLib.aconfig", "settingstheme.aconfig")
                    textProtoRepo = "https://github.com/dot166/platform_build_release"
                }
                """.trimIndent()
        )

        // Run the build
        var command =
            "curl -O https://raw.githubusercontent.com/dot166/jOS_j-lib/refs/tags/v4.4.2/aconfig/jLib.aconfig"
        var processBuilder =
            ProcessBuilder(*command.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())
        processBuilder.directory(projectDir)
        var process = processBuilder.start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw RuntimeException(toString_ReadAllBytes(process.errorStream))
        }
        command =
            "curl -O https://raw.githubusercontent.com/dot166/platform_frameworks_base/refs/heads/16-qpr1/packages/SettingsLib/SettingsTheme/aconfig/settingstheme.aconfig"
        processBuilder =
            ProcessBuilder(*command.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())
        processBuilder.directory(projectDir)
        process = processBuilder.start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw RuntimeException(toString_ReadAllBytes(process.errorStream))
        }
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("build")
        runner.withProjectDir(projectDir)
        runner.build()
    }

    @Test
    @Throws(Exception::class)
    fun testPluginWithOpenJDKKotlin() {
        // Setup the test build
        val projectDir = File("build/test/kotlin")
        deleteDirectory(projectDir)
        Files.createDirectories(projectDir.toPath())
        writeString(
            File(projectDir, "settings.gradle.kts"), """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                    }
                }
                
                dependencyResolutionManagement {
                    repositories {
                        mavenCentral()
                    }
                }
            """.trimIndent())
        writeString(
            File(projectDir, "build.gradle.kts"), """
                plugins {
                    kotlin("jvm") version "+"
                    id("io.github.dot166.aconfig")
                }
                
                aconfig {
                    aconfigFiles = mutableListOf("jLib.aconfig", "settingstheme.aconfig")
                    textProtoRepo = "https://github.com/dot166/platform_build_release"
                }
                """.trimIndent()
        )

        // Run the build
        var command =
            "curl -O https://raw.githubusercontent.com/dot166/jOS_j-lib/refs/tags/v4.4.2/aconfig/jLib.aconfig"
        var processBuilder =
            ProcessBuilder(*command.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())
        processBuilder.directory(projectDir)
        var process = processBuilder.start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw RuntimeException(toString_ReadAllBytes(process.errorStream))
        }
        command =
            "curl -O https://raw.githubusercontent.com/dot166/platform_frameworks_base/refs/heads/16-qpr1/packages/SettingsLib/SettingsTheme/aconfig/settingstheme.aconfig"
        processBuilder =
            ProcessBuilder(*command.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())
        processBuilder.directory(projectDir)
        process = processBuilder.start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw RuntimeException(toString_ReadAllBytes(process.errorStream))
        }
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("build")
        runner.withProjectDir(projectDir)
        runner.build()
    }

    @Throws(IOException::class)
    private fun writeString(file: File, string: String) {
        FileWriter(file).use { writer ->
            writer.write(string)
        }
    }

    @Throws(Exception::class)
    fun toString_ReadAllBytes(stream: InputStream): String {
        val stringBytes = stream.readAllBytes() // read all bytes into a byte array

        return String(stringBytes) // decodes stringBytes into a String
    }

    fun deleteDirectory(directoryToBeDeleted: File): Boolean {
        val allContents = directoryToBeDeleted.listFiles()
        if (allContents != null) {
            for (file in allContents) {
                deleteDirectory(file)
            }
        }
        return directoryToBeDeleted.delete()
    }
}
