package io.github.dot166.aconfig;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.eclipse.jgit.api.Git;
import org.gradle.api.plugins.JavaPluginExtension;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.android.build.api.dsl.ApplicationExtension;
import com.android.build.gradle.BaseExtension;

public class GradleAconfigPlugin implements Plugin<Project> {
    public enum errorCodes {
        Everything_is_Fine,
        Catastrophic_Failure,
        task_ran_manually_with_AGP,
        AGP_Application_not_found
    }

    public File aconfigOutputDir;
    @Override
    public void apply(Project project) {
        AConfigExtension extension = project.getExtensions().create("aconfig", AConfigExtension.class);
        ApplicationExtension androidComponents =
                project.getExtensions().findByType(ApplicationExtension.class);
        AtomicBoolean debuggable = new AtomicBoolean(false);
        File projectDir = project.getProjectDir();
        File buildDir = project.getBuildDir();
        aconfigOutputDir = new File(buildDir, "generated/source/aconfig/");

        project.getTasks().register("generateFlags", task -> {
            task.doLast(t -> {
                File configFile = new File(projectDir, extension.aconfigFile);
                List<File> textProtoFiles = cloneAndFetchTextProtoFiles(t, extension.textProtoRepo, debuggable.get(), extension, buildDir);

                if (configFile.exists()) {
                    Map<String, String> properties = parseAConfig(configFile);
                    Map<String, String> resolvedProperties = resolveTextProtoValues(properties, textProtoFiles, extension, t);
                    generateJavaFile(resolvedProperties, extension);
                    t.getLogger().lifecycle("Generated Flags.java with properties from " + extension.aconfigFile + " and textproto files");
                } else {
                    throw new RuntimeException("No aconfig file found at " + extension.aconfigFile);
                }
            });
        });

        if (project.getPlugins().hasPlugin("com.android.base")) {
            // Configure AGP source sets to include generated sources
            project.getExtensions().configure(BaseExtension.class, android -> {
                android.getSourceSets().getByName("main").getJava().srcDir(aconfigOutputDir);
            });
            project.getTasks().getByName("preBuild").dependsOn("generateFlags");
        } else if (project.getPlugins().hasPlugin("org.gradle.java")) {
            project.getExtensions().getByType(JavaPluginExtension.class)
                .getSourceSets().getByName("main")
                .getJava().srcDir(aconfigOutputDir);
            project.getTasks().getByName("compileJava").dependsOn("generateFlags");
        }

        // Detect which variant is currently being built
        project.getGradle().getTaskGraph().whenReady(graph -> {
            if (project.getPlugins().hasPlugin("com.android.application")) {
                boolean parsed_successfully_at_least_once = false;
                for (Task task : graph.getAllTasks()) {
                    if (task.getName().startsWith("assemble") || task.getName().startsWith("bundle")) {
                        String variantName = task.getName().replace("assemble", "").replace("bundle", "").replace("ClassesToCompileJar", "").replace("ClassesToRuntimeJar", "").replace("LibCompileToJar", "").replace("LibRuntimeToJar", "").replace("LibRuntimeToDir", "").toLowerCase();
                        if (androidComponents != null && !variantName.isBlank()) {
                            debuggable.set(androidComponents.getBuildTypes().getByName(variantName).isDebuggable());
                            parsed_successfully_at_least_once = true;
                        }
                    }
                }
                if (!parsed_successfully_at_least_once) {
                    debuggable.set(fallbackDevelopmentBuildParser(errorCodes.task_ran_manually_with_AGP, project));
                }
            } else {
                debuggable.set(fallbackDevelopmentBuildParser(errorCodes.AGP_Application_not_found, project));
            }
        });
    }

    private boolean fallbackDevelopmentBuildParser(errorCodes errorCode, Project project) {
        switch (errorCode) {
            case AGP_Application_not_found -> {
                if (project.getPlugins().hasPlugin("com.android.library")) {
                    project.getLogger().lifecycle("gradle-aconfig is currently unable to determine if a build of an Android Library project is debuggable");
                    if (!Arrays.toString(project.getTasks().toArray()).contains("preBuild")) {
                        project.getLogger().lifecycle("the generateFlags task may have also been ran manually");
                    }
                } else {
                    project.getLogger().lifecycle("java projects do not have build types");
                }
            }
            case task_ran_manually_with_AGP ->
                    project.getLogger().lifecycle("the generateFlags task may have been ran manually when this is an AGP application project");
            default -> throw new IllegalArgumentException("unexpected error code!");
        }
        // get from command line flags
        Object propObj = project.findProperty("isDebug");
        boolean prop;
        if (propObj != null) {
            prop = (boolean) propObj;
        } else {
            prop = false;
        }
        return prop;
    }

    private Map<String, String> parseAConfig(File file) {
        try {
            return Files.lines(file.toPath())
                    .filter(line -> (line.contains("name: ") || line.contains("package: ")) && !line.startsWith("#"))
                    .map(line -> line.split(": ", 2))
                    .collect(Collectors.toMap(
                            parts -> parts[0].trim(),
                            parts -> parts[1].trim().replace("\"", "")
                    ));
        } catch (IOException e) {
            throw new RuntimeException("Error reading config file", e);
        }
    }

    private Map<String, String> resolveTextProtoValues(Map<String, String> properties, List<File> textProtoFiles, AConfigExtension extension, Task project) {
        Map<String, String> textProtoValues = new HashMap<>();
        for (File file : textProtoFiles) {
            String name = null;
            if (file.exists()) {
                try {
                    List<String> lines = Files.readAllLines(file.toPath());
                    for (String line : lines) {
                        if ((line.contains("name: ") || line.contains("package: ") || line.contains("permission: ") || line.contains("state: ")) && !line.startsWith("#")) {
                            String[] parts = line.split(": ", 2);
                            if (!Objects.equals(properties.get(parts[0].trim()), parts[1].trim().replace("\"", "")) && parts[0].trim().equals("package")) {
                                throw new RuntimeException("package name in " + file.getName() + " does not match the package name in " + extension.aconfigFile);
                            } else if (!Objects.equals(properties.get(parts[0].trim()), extension.flagsPackage) && parts[0].trim().equals("package")) {
                                throw new RuntimeException("package name in the config does not match the package name in " + extension.aconfigFile);
                            } else if (!Objects.equals(parts[1].trim().replace("\"", ""), extension.flagsPackage) && parts[0].trim().equals("package")) {
                                throw new RuntimeException("package name in the config does not match the package name in " + file.getName());
                            }
                            switch (parts[0].trim()) {
                                case "name" -> name = parts[1].trim().replace("\"", "");
                                case "state" -> {
                                    if (textProtoValues.containsKey(name)) {
                                        project.getLogger().lifecycle("value for " + name + " is overridden by the config for it in " + file.getPath().replace(file.getName(), ""));
                                        textProtoValues.remove(name);
                                    }
                                    textProtoValues.put(name, parts[1].trim().replace("\"", ""));
                                }
                                case "permission" -> {
                                    if (parts[1].trim().equals("READ_ONLY")) {
                                        //do nothing, valid supported value
                                    } else if (parts[1].trim().equals("READ_WRITE")) {
                                        project.getLogger().warn("flag {} is a read/write flag, read/write flags are currently not supported, will be treated as a read only flag", name);
                                    } else {
                                        throw new RuntimeException("invalid permission value for " + name);
                                    }
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Error reading textproto file", e);
                }
            }
        }

        Map<String, String> resolvedProperties = new HashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!Objects.equals(entry.getKey(), "package")) {
                resolvedProperties.put(entry.getValue(), parse_aconfig_state(textProtoValues.getOrDefault(entry.getValue(), String.valueOf(false))));
            }
        }
        return resolvedProperties;
    }

    private String parse_aconfig_state(String str) {
        if (Objects.equals(str, "false") || Objects.equals(str, "true")) {
            // nothing to be done here
            return str;
        } else if (Objects.equals(str, "DISABLED")) {
            return "false";
        } else if (Objects.equals(str, "ENABLED")) {
            return "true";
        } else {
            // what, this condition should not be possible
            return "false"; // default
        }
    }

    private List<File> cloneAndFetchTextProtoFiles(Task project, String repoUrl, boolean debuggable, AConfigExtension extension, File buildDir) {
        if (repoUrl == null) throw new RuntimeException("repo url value is not set, please set it using the build.gradle(.kts) file");

        File tempDir = new File(buildDir, "tempRepo");
        deleteDirectory(tempDir);//tempDir.delete();
        tempDir.mkdirs();

        project.getLogger().lifecycle("Cloning repository: " + repoUrl);
        try {
            Git.cloneRepository().setURI(repoUrl).setDirectory(tempDir).call();
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<String> buildFolders = extension.commonBuildValues;
        if (debuggable) {
            buildFolders.add("userdebug");
            if (extension.useENGInDebugBuilds) {
                buildFolders.add("eng");
                if (extension.customDebugBuildValues != null && !extension.customDebugBuildValues.isEmpty()) {
                    buildFolders.addAll(extension.customDebugBuildValues);
                }
            }
        } else {
            buildFolders.add("user");
            if (extension.customReleaseBuildValues != null && !extension.customReleaseBuildValues.isEmpty()) {
                buildFolders.addAll(extension.customReleaseBuildValues);
            }
        }

        List<File> listFiles = new ArrayList<>();
        if (extension.flagsPackage == null) {
            throw new RuntimeException("flags package value is not set, please set it using the build.gradle(.kts) file");
        }
        for (String buildFolder : buildFolders) {
            File targetFolder = new File(tempDir, "aconfig/" + buildFolder + "/" + extension.flagsPackage);
            if (targetFolder.exists() && targetFolder.isDirectory()) {
                File[] files = targetFolder.listFiles((file) -> file.getName().endsWith(".textproto"));
                listFiles.addAll(files != null ? Arrays.asList(files) : Collections.emptyList());
            } else {
                listFiles.addAll(Collections.emptyList());
            }
        }
        return listFiles;
    }

    private void generateJavaFile(Map<String, String> properties, AConfigExtension extension) {
        File outputDir = new File(aconfigOutputDir, extension.flagsPackage.replace(".", "/"));
        outputDir.mkdirs();
        File outputFile = new File(outputDir, "Flags.java");

        StringBuilder classContent = new StringBuilder();
        classContent.append("package ").append(extension.flagsPackage).append(";\n\n");
        classContent.append("public class Flags {\n");
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            classContent.append("    public static boolean ").append(snakeToCamel(entry.getKey())).append("() {\n        return ").append(entry.getValue()).append(";\n    }\n");
        }
        classContent.append("}\n");

        try {
            Files.write(outputFile.toPath(), classContent.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Error writing Java file", e);
        }
    }

    // Function to convert snake case to camel case
    public static String snakeToCamel(String str)
    {
        // Capitalize first letter of string
        // Disabled because compat
        //str = str.substring(0, 1).toUpperCase()
                //+ str.substring(1);

        // Convert to StringBuilder
        StringBuilder builder
                = new StringBuilder(str);

        // Traverse the string character by character and remove underscore and capitalize next letter
        for (int i = 0; i < builder.length(); i++) {

            // Check char is underscore
            if (builder.charAt(i) == '_') {

                builder.deleteCharAt(i);
                builder.replace(
                        i, i + 1,
                        String.valueOf(
                                Character.toUpperCase(
                                        builder.charAt(i))));
            }
        }

        // Return in String type
        return builder.toString();
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
    
    private void createLibaconfig(Project project) {
        String libaconfigName = "libaconfig";
        File libaconfigDir = new File(project.getBuildDir(), libaconfigName);

        // Ensure the subproject directory exists
        if (!libaconfigDir.exists()) {
            libaconfigDir.mkdirs();
        }

        // Create a build.gradle file dynamically
        File buildFile = new File(libaconfigDir, "build.gradle.kts");
        if (!buildFile.exists()) {
            try (FileWriter writer = new FileWriter(buildFile)) {
                writer.write("""
                    plugins {
                        java
                    }

                    dependencies {
                        implementation("org.apache.commons:commons-lang3:3.12.0")
                    }

                    tasks.register("generateSources") {
                        val outputDir = layout.buildDirectory.dir("generated-src").get().asFile
                        doLast {
                            outputDir.mkdirs()
                            val generatedFile = File(outputDir, "generated/GeneratedClass.java")
                            generatedFile.parentFile.mkdirs()
                            generatedFile.writeText(\"""
                                package generated;

                                public class GeneratedClass {
                                    public static String message() {
                                        return "Hello from dynamically created subproject!";
                                    }
                                }
                                \""".trimIndent())
                        }
                    }

                    sourceSets.main {
                        java.srcDir(layout.buildDirectory.dir("generated-src"))
                    }

                    tasks.named("compileJava") {
                        dependsOn("generateSources")
                    }
                """);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create subproject build file", e);
            }
        }

        // Register the subproject dynamically
        project.getGradle().allprojects(proj -> {
            if (proj.getProjectDir().equals(libaconfigDir)) {
                configureSubproject(proj);
            }
        });

        // Ensure the subproject is available
        project.getGradle().projectsLoaded(gradle -> {
            if (project.findProject(libaconfigName) == null) {
                Project libaconfig = project.getRootProject().project(":build:" + libaconfigName);
                configureSubproject(libaconfig);

                project.getDependencies().add("implementation", libaconfig);
            }
        });
    }

    private void configureSubproject(Project subproject) {
        subproject.getPlugins().apply("java");

        // Ensure the sources are generated before compilation
        subproject.getTasks().named("compileJava").configure(task -> {
            task.dependsOn("generateSources");
        });
    }

}


