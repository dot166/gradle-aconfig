package io.github.dot166.aconfig;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.eclipse.jgit.api.Git;

import java.io.File;
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

public class GradleAconfigPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        AConfigExtension extension = project.getExtensions().create("aconfig", AConfigExtension.class);
        ApplicationExtension androidComponents =
                project.getExtensions().findByType(ApplicationExtension.class);
        AtomicBoolean debuggable = new AtomicBoolean(false);
        File projectDir = project.getProjectDir();
        File buildDir = project.getBuildDir();

        project.getTasks().register("generateFlags", task -> {
            task.doLast(t -> {
                File configFile = new File(projectDir, extension.aconfigFile);
                List<File> textProtoFiles = cloneAndFetchTextProtoFiles(t, extension.textProtoRepo, debuggable.get(), extension, buildDir);

                if (configFile.exists()) {
                    Map<String, String> properties = parseAConfig(configFile);
                    Map<String, String> resolvedProperties = resolveTextProtoValues(properties, textProtoFiles, extension, t);
                    generateJavaFile(t, resolvedProperties, extension, buildDir);
                    t.getLogger().lifecycle("Generated Flags.java with properties from " + extension.aconfigFile + " and textproto files");
                } else {
                    throw new RuntimeException("No aconfig file found at " + extension.aconfigFile);
                }
            });
        });

        try {
            project.getTasks().getByName("preBuild").dependsOn("generateFlags");
        } catch (Throwable throwable) {
            project.getLogger().error("Couldn't link generateFlags task to agp");
            project.getLogger().error("if you are using agp (com.android.application or com.android.library plugins) make sure that this plugin is applied after agp in your build files");
            throwable.printStackTrace();
        }

        // Detect which variant is currently being built
        project.getGradle().getTaskGraph().whenReady(graph -> {
            if (project.getPlugins().hasPlugin("com.android.application")) {
                for (Task task : graph.getAllTasks()) {
                    if (task.getName().startsWith("assemble") || task.getName().startsWith("bundle")) {
                        String variantName = task.getName().replace("assemble", "").replace("bundle", "").replace("ClassesToCompileJar", "").replace("ClassesToRuntimeJar", "").toLowerCase();
                        if (androidComponents != null && !variantName.isBlank()) {
                            debuggable.set(androidComponents.getBuildTypes().getByName(variantName).isDebuggable());
                        } else {
                            debuggable.set(false);
                        }
                    }
                }
            } else {
                // TODO: fix support for java applications and libraries and android libraries as this relies on android application agp for build types
                // get from command line flags
            }
        });
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
                            if (!Objects.equals(properties.get(parts[0].trim()), parts[1].trim()) && parts[0].trim().equals("package")) {
                                throw new RuntimeException("package name in " + file.getName() + " does not match the package name in " + extension.aconfigFile);
                            } else if (!Objects.equals(properties.get(parts[0].trim()), extension.flagsPackage) && parts[0].trim().equals("package")) {
                                throw new RuntimeException("package name in the config does not match the package name in " + extension.aconfigFile);
                            } else if (!Objects.equals(parts[1].trim(), extension.flagsPackage) && parts[0].trim().equals("package")) {
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
                resolvedProperties.put(entry.getValue(), textProtoValues.getOrDefault(entry.getValue(), String.valueOf(false)));
            }
        }
        return resolvedProperties;
    }

    private List<File> cloneAndFetchTextProtoFiles(Task project, String repoUrl, boolean debuggable, AConfigExtension extension, File buildDir) {
        if (repoUrl == null) return Collections.emptyList();

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
            }
        } else {
            buildFolders.add("user");
        }

        List<File> listFiles = new ArrayList<>();
        if (extension.flagsPackage == null) {
            throw new RuntimeException("flags package value is not set, please set it using the build.gradle file");
        }
        for (int i = 0; i < buildFolders.size(); i++) {
            File targetFolder = new File(tempDir, "aconfig/" + buildFolders.get(i) + "/" + extension.flagsPackage);
            if (targetFolder.exists() && targetFolder.isDirectory()) {
                File[] files = targetFolder.listFiles((file) -> file.getName().endsWith(".textproto"));
                listFiles.addAll(files != null ? Arrays.asList(files) : Collections.emptyList());
            } else {
                listFiles.addAll(Collections.emptyList());
            }
        }
        return listFiles;
    }

    private void generateJavaFile(Task project, Map<String, String> properties, AConfigExtension extension, File buildDir) {
        File outputDir = new File(buildDir, "generated/sources/aconfig");
        outputDir.mkdirs();
        File outputFile = new File(outputDir, "Flags.java");

        StringBuilder classContent = new StringBuilder();
        classContent.append("package ").append(extension.flagsPackage).append(";\n\n");
        classContent.append("public class Flags {\n");
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            classContent.append("    public static boolean ").append(snakeToCamel(entry.getKey())).append("() {\n    return ").append(entry.getValue()).append(";\n    }\n");
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

}


