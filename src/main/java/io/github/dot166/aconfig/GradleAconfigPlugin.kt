package io.github.dot166.aconfig

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginExtension
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

class GradleAconfigPlugin : Plugin<Project?> {
    var aconfigOutputDir: File? = null
    override fun apply(project: Project) {
        val extension = project.extensions
            .create("aconfig", AConfigExtension::class.java)
        val androidComponents =
            project.extensions
                .findByType(AndroidComponentsExtension::class.java)
        var debuggable = false
        val projectDir = project.projectDir
        val buildDir = project.layout.buildDirectory.get().asFile
        aconfigOutputDir = File(buildDir, "generated/source/aconfig/")

        project.tasks.register("generateFlags") {
            doLast {
                val properties: MutableList<AConfig> = ArrayList()
                val paths: MutableList<String> = extension.aconfigFiles
                for (i in paths.indices) {
                    val configFile = File(projectDir, paths[i])
                    if (configFile.exists()) {
                        properties.add(parseAConfig(configFile))
                    } else {
                        logger.error("No aconfig file found at {}", paths[i])
                    }
                }
                if (!properties.isEmpty()) {
                    generateJavaFile(this, debuggable, extension, buildDir, properties)
                    logger
                        .lifecycle("Generated Flags.java with properties from " + extension.aconfigFiles + " and textproto files")
                } else {
                    throw RuntimeException("No aconfig files found at " + extension.aconfigFiles)
                }
            }
        }

        if (project.plugins.hasPlugin("com.android.base")) {
            project.extensions.configure(
                BaseExtension::class.java)
                {
                    sourceSets.getByName("main").java.srcDir(aconfigOutputDir!!)
                }
            project.tasks.getByName("preBuild").dependsOn("generateFlags")

            androidComponents!!.onVariants(
                androidComponents.selector().all()){ variant: Variant ->
                    if (project.plugins.hasPlugin("com.android.application")) {
                        debuggable = variant.debuggable
                    } else {
                        val buildType = variant.buildType
                        debuggable = buildType != "release"
                    }
                }
        } else if (project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
            project.extensions.getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName("main")
                .java.srcDir(aconfigOutputDir!!)
            project.tasks.getByName("compileKotlin").dependsOn("generateFlags")
            project.logger.lifecycle("kotlin projects do not have build types")
            val propObj = project.findProperty("isDebug")
            val prop: Boolean = if (propObj != null) {
                propObj as Boolean
            } else {
                false
            }
            debuggable = prop
        } else if (project.plugins.hasPlugin("org.gradle.java")) {
            project.extensions.getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName("main")
                .java.srcDir(aconfigOutputDir!!)
            project.tasks.getByName("compileJava").dependsOn("generateFlags")
            project.logger.lifecycle("java projects do not have build types")
            val propObj = project.findProperty("isDebug")
            val prop: Boolean = if (propObj != null) {
                propObj as Boolean
            } else {
                false
            }
            debuggable = prop
        }
    }

    private fun parseAConfig(file: File): AConfig {
        var packageName: String? = null
        val flags: MutableList<AConfig.Flag> = ArrayList()

        try {
            val lines = Files.readAllLines(file.toPath())

            var insideFlag = false

            for (rawLine in lines) {
                val line = rawLine.trim { it <= ' ' }

                if (line.startsWith("package:")) {
                    packageName = line.split(": ".toRegex(), limit = 2).toTypedArray()[1]
                        .replace("\"", "")
                        .trim { it <= ' ' }
                    continue
                }

                if (line.startsWith("flag {")) {
                    insideFlag = true
                    continue
                }

                if (line.startsWith("}")) {
                    insideFlag = false
                    continue
                }

                if (insideFlag && line.startsWith("name:")) {
                    val flagName = line.split(": ".toRegex(), limit = 2).toTypedArray()[1]
                        .replace("\"", "")
                        .trim { it <= ' ' }
                    flags.add(AConfig.Flag(flagName))
                }
            }

            return AConfig(packageName!!, flags)
        } catch (e: IOException) {
            throw RuntimeException("Error reading config file", e)
        }
    }

    private fun parseReadWriteFlagAllowed(tmpDir: File): Boolean {
        var value: Boolean = false // default

        try {
            val lines = Files.readAllLines(File(tmpDir, "flag_values/bp1a/RELEASE_ACONFIG_REQUIRE_ALL_READ_ONLY.textproto").toPath())

            var insideFlag = false

            for (rawLine in lines) {
                val line = rawLine.trim { it <= ' ' }

                if (line.startsWith("name:")) {
                    if (line.split(": ".toRegex(), limit = 2).toTypedArray()[1]
                        .replace("\"", "")
                        .trim { it <= ' ' } != "RELEASE_ACONFIG_REQUIRE_ALL_READ_ONLY") {
                        throw RuntimeException("ERROR!, Required File is missing or corrupted")
                    }
                    continue
                }

                if (line.startsWith("value: {")) {
                    insideFlag = true
                    continue
                }

                if (line.startsWith("}")) {
                    insideFlag = false
                    continue
                }

                if (insideFlag && line.startsWith("bool_value:")) {
                    val flagValue = line.split(": ".toRegex(), limit = 2).toTypedArray()[1]
                        .replace("\"", "")
                        .trim { it <= ' ' }
                    value = !parseState(flagValue)
                }
            }

            return value
        } catch (e: IOException) {
            throw RuntimeException("ERROR!, Required File is missing or corrupted", e)
        }
    }

    private fun parseState(str: String?): Boolean {
        return when (str) {
            "false", "true" -> {
                str.toBoolean()
            }
            "DISABLED" -> {
                false
            }
            "ENABLED" -> {
                true
            }
            else -> {
                false
            }
        }
    }

    private fun generateJavaFile(
        project: Task,
        debuggable: Boolean,
        extension: AConfigExtension,
        buildDir: File,
        properties: MutableList<AConfig>
    ) {
        val repoUrl = extension.textProtoRepo
            ?: throw RuntimeException("repo url value is not set, please set it using the build.gradle(.kts) file")

        val tempDir = File(buildDir, "tempRepo")
        deleteDirectory(tempDir)
        tempDir.mkdirs()

        project.logger.lifecycle("Cloning repository: $repoUrl")
        try {
            var command =
                "git clone --no-checkout --depth=1 --filter=tree:0 " + repoUrl + " " + tempDir.path
            var processBuilder =
                ProcessBuilder(*command.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray())
            processBuilder.directory(buildDir)
            var process = processBuilder.start()
            process.waitFor()
            if (process.exitValue() != 0) {
                throw RuntimeException(toStringReadAllBytes(process.errorStream))
            }

            command = "git sparse-checkout set --no-cone /aconfig"
            processBuilder =
                ProcessBuilder(*command.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray())
            processBuilder.directory(tempDir)
            process = processBuilder.start()
            process.waitFor()
            if (process.exitValue() != 0) {
                throw RuntimeException(toStringReadAllBytes(process.errorStream))
            }

            command = "git sparse-checkout add /flag_values/bp1a"
            processBuilder =
                ProcessBuilder(*command.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray())
            processBuilder.directory(tempDir)
            process = processBuilder.start()
            process.waitFor()
            if (process.exitValue() != 0) {
                throw RuntimeException(toStringReadAllBytes(process.errorStream))
            }

            command = "git checkout"
            processBuilder =
                ProcessBuilder(*command.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray())
            processBuilder.directory(tempDir)
            process = processBuilder.start()
            process.waitFor()
            if (process.exitValue() != 0) {
                throw RuntimeException(toStringReadAllBytes(process.errorStream))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val buildFolders: MutableList<String> = mutableListOf("root", "ap2a", "ap3a", "ap4a", "bp1a", "bp2a", "bp3a", "bp4a")
        if (debuggable) {
            buildFolders.add("userdebug")
            buildFolders.add("eng")
            if (!extension.customDebugBuildValues.isEmpty()) {
                buildFolders.addAll(extension.customDebugBuildValues)
            }
        } else {
            buildFolders.add("user")
            if (!extension.customReleaseBuildValues.isEmpty()) {
                buildFolders.addAll(extension.customReleaseBuildValues)
            }
        }

        val listFiles: MutableList<File> = ArrayList()
        for (buildFolder in buildFolders) {
            for (aconfig in properties) {
                val targetFolder =
                    File(tempDir, "aconfig/" + buildFolder + "/" + aconfig.packageName)
                if (targetFolder.exists() && targetFolder.isDirectory) {
                    val files = targetFolder.listFiles { file: File ->
                        file.name.endsWith(".textproto")
                    }
                    listFiles.addAll(listOf<File>(*files))
                } else {
                    listFiles.addAll(mutableListOf())
                }
            }
        }
        val textProtoValues: MutableMap<String?, String?> = HashMap()
        for (file in listFiles) {
            var name: String? = null
            if (file.exists()) {
                try {
                    val lines = Files.readAllLines(file.toPath())
                    for (line in lines) {
                        if ((line.contains("name: ") || line.contains("package: ") || line.contains(
                                "permission: "
                            ) || line.contains("state: ")) && !line.startsWith("#")
                        ) {
                            val parts: Array<String?> =
                                line.split(": ".toRegex(), limit = 2).toTypedArray()
                            if (!properties.contains(
                                    parts[1]!!.trim { it <= ' ' }.replace(
                                        "\"",
                                        ""
                                    )
                                ) && parts[0]!!.trim { it <= ' ' } == "package"
                            ) {
                                throw RuntimeException("package name in " + file.name + " does not match the package name in one of the following files " + extension.aconfigFiles)
                            }
                            when (parts[0]!!.trim { it <= ' ' }) {
                                "name" -> name = parts[1]!!.trim { it <= ' ' }.replace("\"", "")
                                "state" -> {
                                    if (textProtoValues.containsKey(name)) {
                                        project.logger.lifecycle(
                                            "value for $name is overridden by the config for it in " + file.path
                                                .replace(file.name, "")
                                        )
                                        textProtoValues.remove(name)
                                    }
                                    textProtoValues[name] =
                                        parts[1]!!.trim { it <= ' ' }.replace("\"", "")
                                }

                                "permission" -> {
                                    if (parts[1]!!.trim { it <= ' ' } == "READ_WRITE" && parseReadWriteFlagAllowed(tempDir)) {
                                        throw RuntimeException("read-write flags are not allowed in aosp, it is disabled by flag 'RELEASE_ACONFIG_REQUIRE_ALL_READ_ONLY'")
                                    } else if (parts[1]!!.trim { it <= ' ' } != "READ_ONLY" && parts[1]!!.trim { it <= ' ' } != "READ_WRITE") {
                                        val perm = parts[1]!!.trim { it <= ' ' }
                                        throw RuntimeException("invalid permission '$perm' for $name")
                                    }
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    throw RuntimeException("Error reading textproto file", e)
                }
            }
        }
        for (i in properties.indices) {
            val entry = properties[i]
            for (j in entry.flags.indices) {
                val flag = entry.flags[j]
                flag.value =
                    parseState(textProtoValues.getOrDefault(flag.name, false.toString()))
            }
        }
        for (aConfigFile in properties) {
            val outputDir = File(aconfigOutputDir, aConfigFile.packageName.replace(".", "/"))
            outputDir.mkdirs()
            val outputFile = File(outputDir, "Flags.java")

            val classContent = StringBuilder()
            classContent.append("package ").append(aConfigFile.packageName).append(";\n\n")
            classContent.append("public class Flags {\n")
            for (i in aConfigFile.flags.indices) {
                val entry = aConfigFile.flags[i]
                val method = StringBuilder()
                method.append("    public static boolean ").append(entry.name.toCamelCase())
                    .append("() {\n        return ")
                method.append(entry.value)
                method.append(";\n    }\n")
                classContent.append(method.toString())
            }
            classContent.append("}\n")

            try {
                Files.write(outputFile.toPath(), classContent.toString().toByteArray())
            } catch (e: IOException) {
                throw RuntimeException("Error writing Flags Java file", e)
            }
        }
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

    private fun MutableList<AConfig>.contains(str: String): Boolean {
        var contains = false
        for (i in 0 until size) {
            if (get(i).packageName == str) {
                contains = true
            }
        }
        return contains
    }

    fun String.toCamelCase(): String {
        // This regex finds underscores or hyphens followed by any character (or end of string)
        val pattern = "[-_]+(.)?".toRegex()

        val camelCaseString = this.replace(pattern) { match ->
            // match.groups[1] is the character after the delimiter
            match.groups[1]?.value?.uppercase() ?: ""
        }

        // Ensure the very first character is lowercase, as per standard camelCase convention
        return camelCaseString.replaceFirstChar { it.lowercase() }
    }

    companion object {

        @Throws(IOException::class)
        fun toStringReadAllBytes(stream: InputStream): String {
            val stringBytes = stream.readAllBytes()
            return String(stringBytes)
        }
    }
}


