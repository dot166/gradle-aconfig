package io.github.dot166.aconfig;

import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.eclipse.jgit.api.Git;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.android.build.api.dsl.ApplicationBuildType;
import com.android.build.api.dsl.ApplicationExtension;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.ApplicationVariant;
import com.android.build.gradle.BaseExtension;
import com.android.build.api.dsl.BuildType;
import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import com.android.build.api.variant.Variant;

public class GradleAconfigPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        AConfigExtension extension = project.getExtensions().create("aconfig", AConfigExtension.class);

        // Detect which variant is currently being built
        project.getGradle().getTaskGraph().whenReady(graph -> {
            for (Task task : graph.getAllTasks()) {
                if (task.getName().startsWith("assemble") || task.getName().startsWith("bundle")) {
                    String variantName = task.getName().replace("assemble", "").replace("bundle", "").toLowerCase();
                    File configFile = project.file(extension.aconfigFile);
                    String buildType = (String) project.getProperties().get("buildType");
                    String selectedFolder = extension.buildTypeMapping.getOrDefault(buildType, "release");
                    List<File> textProtoFiles = cloneAndFetchTextProtoFiles(project, extension.textProtoRepo, selectedFolder, extension);

                    project.getLogger().lifecycle(buildType);
                    project.getLogger().lifecycle(selectedFolder);
                    if (configFile.exists()) {
                        Map<String, String> properties = parseAConfig(configFile);
                        Map<String, String> resolvedProperties = resolveTextProtoValues(properties, textProtoFiles, extension, project);
                        generateJavaFile(project, resolvedProperties, extension);
                        project.getLogger().lifecycle("Generated Flags.java with properties from " + extension.aconfigFile + " and textproto files");
                    } else {
                        project.getLogger().lifecycle("No aconfig file found at " + extension.aconfigFile);
                    }
                }
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

    private Map<String, String> resolveTextProtoValues(Map<String, String> properties, List<File> textProtoFiles, AConfigExtension extension, Project project) {
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
                                case "state" ->
                                        textProtoValues.put(name, parts[1].trim().replace("\"", ""));
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

    private List<File> cloneAndFetchTextProtoFiles(Project project, String repoUrl, String selectedFolder, AConfigExtension extension) {
        if (repoUrl == null) return Collections.emptyList();

        File tempDir = new File(project.getBuildDir(), "tempRepo");
        tempDir.delete();
        tempDir.mkdirs();

        project.getLogger().lifecycle("Cloning repository: " + repoUrl);
        try {
            Git.cloneRepository().setURI(repoUrl).setDirectory(tempDir).call();
        } catch (Exception e) {
            e.printStackTrace();
        }

        File targetFolder = new File(tempDir, "aconfig/" + selectedFolder + "/" + extension.flagsPackage);
        if (targetFolder.exists() && targetFolder.isDirectory()) {
            File[] files = targetFolder.listFiles((file) -> file.getName().endsWith(".textproto"));
            return files != null ? Arrays.asList(files) : Collections.emptyList();
        } else {
            return Collections.emptyList();
        }
    }

    private void generateJavaFile(Project project, Map<String, String> properties, AConfigExtension extension) {
        File outputDir = new File(project.getBuildDir(), "generated/sources/aconfig");
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

}


