package io.github.dot166.aconfig;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class GradleAconfigPlugin implements Plugin<Project> {
    String pkg;
    @Override
    public void apply(Project project) {
        pkg = null;
        String configFilePath = (String) project.findProperty("aconfigFile");
        if (configFilePath == null) {
            configFilePath = "config.aconfig";
        }
        File configFile = project.file(configFilePath);
        List<File> textProtoFiles = Optional.ofNullable((String) project.findProperty("textProtoFiles"))
                .map(files -> Arrays.stream(files.split(","))
                        .map(project::file)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());

        project.getLogger().lifecycle(String.valueOf(configFile.exists()));
        if (configFile.exists()) {
            Map<String, String> properties = parseAConfig(configFile);
            Map<String, String> resolvedProperties = resolveTextProtoValues(properties, textProtoFiles);
            project.getLogger().lifecycle("Generated AConfig.java with properties from " + configFilePath + " and textproto files");
        } else {
            project.getLogger().lifecycle("No .aconfig file found at " + configFilePath);
        }
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

    private Map<String, String> resolveTextProtoValues(Map<String, String> properties, List<File> textProtoFiles) {
        Map<String, String> textProtoValues = new HashMap<>();
        for (File file : textProtoFiles) {
            if (file.exists()) {
                try {
                    List<String> lines = Files.readAllLines(file.toPath());
                    for (String line : lines) {
                        if ((line.contains("name: ") || line.contains("package: ") || line.contains("state: ")) && !line.startsWith("#")) {
                            String[] parts = line.split(": ", 2);
                            if (!Objects.equals(properties.get(parts[0].trim()), parts[1].trim()) && !parts[0].trim().equals("package")) {
                                throw new RuntimeException("package name in the textproto file does not match the package name in the aconfig file");
                            }
                            textProtoValues.put(parts[0].trim(), parts[1].trim().replace("\"", ""));
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Error reading textproto file", e);
                }
            }
        }

        Map<String, String> resolvedProperties = new HashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (Objects.equals(entry.getKey(), "package")) {
                pkg = entry.getValue();
            } else {
                resolvedProperties.put(entry.getValue(), textProtoValues.getOrDefault(entry.getValue(), String.valueOf(false)));
            }
        }
        return resolvedProperties;
    }

    private void generateJavaFile(Project project, Map<String, String> properties) {
        File outputDir = new File(project.getBuildDir(), "generated/sources/aconfig");
        outputDir.mkdirs();
        File outputFile = new File(outputDir, "Flags.java");

        StringBuilder classContent = new StringBuilder();
        classContent.append("package ").append(pkg).append(";\n\n");
        classContent.append("public class Flags {\n");
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            classContent.append("    public static final boolean ").append(entry.getKey().toUpperCase()).append(" = ").append(entry.getValue()).append(";\n");
        }
        classContent.append("}\n");

        try {
            Files.write(outputFile.toPath(), classContent.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Error writing Java file", e);
        }
    }

    private List<File> getTextProtoFiles(Project project, String textProtoFolderPath) {
        File folder = project.file(textProtoFolderPath);
        if (folder.exists() && folder.isDirectory()) {
            return Arrays.asList(Objects.requireNonNull(folder.listFiles(file -> file.getName().endsWith(".textproto"))));
        } else {
            return Collections.emptyList();
        }
    }
}


