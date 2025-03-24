/*
 * This source file was generated by the Gradle 'init' task
 */
package io.github.dot166.aconfig;

import org.apache.commons.io.FileUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;

/**
 * A simple unit test for the 'io.github.dot166.aconfig' plugin.
 */
class GradleAconfigPluginTest {
    @Test void testPluginWithAGPAsApplication() throws Exception {
        // Setup the test build
        File projectDir = new File("build/test/agp/application");
        deleteDirectory(projectDir);
        Files.createDirectories(projectDir.toPath());
        File manifestDir = new File(projectDir.getAbsolutePath() + "/src/main");
        Files.createDirectories(manifestDir.toPath());
        writeString(new File(projectDir, "settings.gradle.kts"), """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        google()
                        mavenCentral()
                        mavenLocal()
                    }
                }
                
                 dependencyResolutionManagement {
                     repositories {
                         google()
                         mavenCentral()
                     }
                 }""");
        writeString(new File(projectDir, "build.gradle.kts"), """
                plugins {
                    id("com.android.application")
                    id("io.github.dot166.aconfig")
                }
                
                android {
                    namespace = "io.github.dot166.aconfig.test"
                    compileSdk = 35
                }
                
                aconfig {
                    aconfigFile = "jLib.aconfig"
                    textProtoRepo = "https://github.com/dot166/platform_build_release"
                    flagsPackage = "io.github.dot166.jlib.flags"
                }""");
        writeString(new File(projectDir, "local.properties"), "sdk.dir=" + projectDir.getAbsolutePath() + "/Sdk");
        writeString(new File(manifestDir, "AndroidManifest.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools">
                
                    <application/>
                
                </manifest>""");

        // Run the build
        copySdk(projectDir);
        String command =
                "curl -O https://raw.githubusercontent.com/dot166/jOS_j-lib/refs/heads/main/aconfig/jLib.aconfig";
        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        processBuilder.directory(projectDir);
        Process process = processBuilder.start();
        process.waitFor();
        if (process.exitValue() != GradleAconfigPlugin.errorCodes.Everything_is_Fine.ordinal()) {
            throw new RuntimeException(toString_ReadAllBytes(process.getErrorStream()));
        }
        GradleRunner runner = GradleRunner.create();
        runner.forwardOutput();
        runner.withPluginClasspath();
        runner.withArguments("assembleDebug");
        runner.withProjectDir(projectDir);
        BuildResult result = runner.build();
    }
    @Test void testPluginWithAGPAsLibrary() throws Exception {
        // Setup the test build
        File projectDir = new File("build/test/agp/library");
        deleteDirectory(projectDir);
        Files.createDirectories(projectDir.toPath());
        writeString(new File(projectDir, "settings.gradle.kts"), """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        google()
                        mavenCentral()
                        mavenLocal()
                    }
                }
                
                 dependencyResolutionManagement {
                     repositories {
                         google()
                         mavenCentral()
                     }
                 }""");
        writeString(new File(projectDir, "build.gradle.kts"), """
                plugins {
                    id("com.android.library")
                    id("io.github.dot166.aconfig")
                }
                
                android {
                    namespace = "io.github.dot166.aconfig.test"
                    compileSdk = 35
                }
                
                aconfig {
                    aconfigFile = "jLib.aconfig"
                    textProtoRepo = "https://github.com/dot166/platform_build_release"
                    flagsPackage = "io.github.dot166.jlib.flags"
                }""");
        writeString(new File(projectDir, "local.properties"), "sdk.dir=" + projectDir.getAbsolutePath() + "/Sdk");

        // Run the build
        copySdk(projectDir);
        String command =
                "curl -O https://raw.githubusercontent.com/dot166/jOS_j-lib/refs/heads/main/aconfig/jLib.aconfig";
        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        processBuilder.directory(projectDir);
        Process process = processBuilder.start();
        process.waitFor();
        if (process.exitValue() != GradleAconfigPlugin.errorCodes.Everything_is_Fine.ordinal()) {
            throw new RuntimeException(toString_ReadAllBytes(process.getErrorStream()));
        }
        GradleRunner runner = GradleRunner.create();
        runner.forwardOutput();
        runner.withPluginClasspath();
        runner.withArguments("assembleDebug");
        runner.withProjectDir(projectDir);
        BuildResult result = runner.build();
    }
    @Test void testPluginWithOpenJDK() throws Exception {
        // Setup the test build
        File projectDir = new File("build/test/java");
        deleteDirectory(projectDir);
        Files.createDirectories(projectDir.toPath());
        writeString(new File(projectDir, "settings.gradle.kts"), "");
        writeString(new File(projectDir, "build.gradle.kts"), """
                plugins {
                    `java`
                    id("io.github.dot166.aconfig")
                }
                
                aconfig {
                    aconfigFile = "jLib.aconfig"
                    textProtoRepo = "https://github.com/dot166/platform_build_release"
                    flagsPackage = "io.github.dot166.jlib.flags"
                }""");

        // Run the build
        String command =
                "curl -O https://raw.githubusercontent.com/dot166/jOS_j-lib/refs/heads/main/aconfig/jLib.aconfig";
        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        processBuilder.directory(projectDir);
        Process process = processBuilder.start();
        process.waitFor();
        if (process.exitValue() != GradleAconfigPlugin.errorCodes.Everything_is_Fine.ordinal()) {
            throw new RuntimeException(toString_ReadAllBytes(process.getErrorStream()));
        }
        GradleRunner runner = GradleRunner.create();
        runner.forwardOutput();
        runner.withPluginClasspath();
        runner.withArguments("build");
        runner.withProjectDir(projectDir);
        BuildResult result = runner.build();
    }

    private void writeString(File file, String string) throws IOException {
        try (Writer writer = new FileWriter(file)) {
            writer.write(string);
        }
    }
    public String toString_ReadAllBytes(InputStream stream) throws Exception {

        byte[] stringBytes = stream.readAllBytes(); // read all bytes into a byte array

        return new String(stringBytes);// decodes stringBytes into a String
    }

    boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    void copySdk(File projectDir) {
        File srcDir = new File(FileUtils.getUserDirectory().getAbsolutePath() + "/Android/Sdk");
        File destDir = new File(projectDir.getAbsolutePath() + "/Sdk");

        try {
            FileUtils.copyDirectory(srcDir, destDir);
        } catch (IOException e) {
            e.printStackTrace();
        }}

}
