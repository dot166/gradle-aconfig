package io.github.dot166.aconfig;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginExtension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

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
                List<AConfig> properties = new ArrayList<AConfig>();
                List<String> paths = extension.aconfigFiles;
                for (int i = 0; i < paths.size(); i++) {
                    File configFile = new File(projectDir, paths.get(i));
                    if (configFile.exists()) {
                        properties.add(parseAConfig(configFile));
                    } else {
                        t.getLogger().error("No aconfig file found at {}", paths.get(i));
                    }
                }

                if (!properties.isEmpty()) {
                    generateJavaFile(t, debuggable.get(), extension, buildDir, properties);
                    t.getLogger().lifecycle("Generated Flags.java with properties from " + extension.aconfigFiles + " and textproto files");
                } else {
                    throw new RuntimeException("No aconfig files found at " + extension.aconfigFiles);
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
                        String variantName = task.getName().replace("assemble", "").replace("bundle", "").replace("ClassesToCompileJar", "").replace("ClassesToRuntimeJar", "").replace("LibCompileToJar", "").replace("LibRuntimeToJar", "").replace("LibRuntimeToDir", "").replace("Aar", "").replace("LocalLint", "").replace("Resources", "").toLowerCase();
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
                    if (!Arrays.toString(project.getTasks().toArray()).contains("compileJava")) {
                        project.getLogger().lifecycle("the generateFlags task may have also been ran manually");
                    }
                }
            }
            case task_ran_manually_with_AGP ->
                    project.getLogger().lifecycle("the generateFlags task may have been ran manually");
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

    private AConfig parseAConfig(File file) {
        String packageName = null;
        List<Flag> flags = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(file.toPath());

            boolean insideFlag = false;

            for (String rawLine : lines) {
                String line = rawLine.trim();

                // Package at the top
                if (line.startsWith("package:")) {
                    packageName = line.split(": ", 2)[1]
                            .replace("\"", "")
                            .trim();
                    continue;
                }

                // Enter flag block
                if (line.startsWith("flag {")) {
                    insideFlag = true;
                    continue;
                }

                // Exit flag block
                if (line.startsWith("}")) {
                    insideFlag = false;
                    continue;
                }

                // Capture flag name
                if (insideFlag && line.startsWith("name:")) {
                    String flagName = line.split(": ", 2)[1]
                            .replace("\"", "")
                            .trim();
                    flags.add(new Flag(flagName));
                }
            }

            return new AConfig(packageName, flags);

        } catch (IOException e) {
            throw new RuntimeException("Error reading config file", e);
        }
    }

    private Boolean parse_aconfig_state(String str) {
        if (Objects.equals(str, "false") || Objects.equals(str, "true")) {
            // nothing to be done here
            return Boolean.parseBoolean(str);
        } else if (Objects.equals(str, "DISABLED")) {
            return false;
        } else if (Objects.equals(str, "ENABLED")) {
            return true;
        } else {
            // what, this condition should not be possible
            return false; // default
        }
    }

    private void generateJavaFile(Task project, boolean debuggable, AConfigExtension extension, File buildDir, List<AConfig> properties) {
        String repoUrl = extension.textProtoRepo;
        if (repoUrl == null) throw new RuntimeException("repo url value is not set, please set it using the build.gradle(.kts) file");

        File tempDir = new File(buildDir, "tempRepo");
        deleteDirectory(tempDir);//tempDir.delete();
        tempDir.mkdirs();

        project.getLogger().lifecycle("Cloning repository: " + repoUrl);
        try {
            String command = "git clone --no-checkout --depth=1 --filter=tree:0 " + repoUrl + " " + tempDir.getPath();
            ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
            processBuilder.directory(buildDir);
            Process process = processBuilder.start();
            process.waitFor();
            if (process.exitValue() != errorCodes.Everything_is_Fine.ordinal()) {
                throw new RuntimeException(toStringReadAllBytes(process.getErrorStream()));
            }

            command = "git sparse-checkout set --no-cone /aconfig";
            processBuilder = new ProcessBuilder(command.split(" "));
            processBuilder.directory(tempDir);
            process = processBuilder.start();
            process.waitFor();
            if (process.exitValue() != errorCodes.Everything_is_Fine.ordinal()) {
                throw new RuntimeException(toStringReadAllBytes(process.getErrorStream()));
            }

            command = "git checkout";
            processBuilder = new ProcessBuilder(command.split(" "));
            processBuilder.directory(tempDir);
            process = processBuilder.start();
            process.waitFor();
            if (process.exitValue() != errorCodes.Everything_is_Fine.ordinal()) {
                throw new RuntimeException(toStringReadAllBytes(process.getErrorStream()));
            }//Git.cloneRepository().setURI(repoUrl).setDirectory(tempDir).call();
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<String> buildFolders = extension.commonBuildValues;
        if (debuggable) {
            buildFolders.add("userdebug");
            buildFolders.add("eng");
            if (!extension.customDebugBuildValues.isEmpty()) {
                buildFolders.addAll(extension.customDebugBuildValues);
            }
        } else {
            buildFolders.add("user");
            if (!extension.customReleaseBuildValues.isEmpty()) {
                buildFolders.addAll(extension.customReleaseBuildValues);
            }
        }

        List<File> listFiles = new ArrayList<>();
        for (String buildFolder : buildFolders) {
            for (AConfig aconfig : properties) {
                File targetFolder = new File(tempDir, "aconfig/" + buildFolder + "/" + aconfig.packageName);
                if (targetFolder.exists() && targetFolder.isDirectory()) {
                    File[] files = targetFolder.listFiles((file) -> file.getName().endsWith(".textproto"));
                    listFiles.addAll(files != null ? Arrays.asList(files) : Collections.emptyList());
                } else {
                    listFiles.addAll(Collections.emptyList());
                }
            }
        }
        Map<String, String> textProtoValues = new HashMap<>();
        for (File file : listFiles) {
            String name = null;
            if (file.exists()) {
                try {
                    List<String> lines = Files.readAllLines(file.toPath());
                    for (String line : lines) {
                        if ((line.contains("name: ") || line.contains("package: ") || line.contains("permission: ") || line.contains("state: ")) && !line.startsWith("#")) {
                            String[] parts = line.split(": ", 2);
                            if (!ListUtils.containsStr(properties, parts[1].trim().replace("\"", "")) && parts[0].trim().equals("package")) {
                                throw new RuntimeException("package name in " + file.getName() + " does not match the package name in one of the following files " + extension.aconfigFiles);
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
                                    if (!parts[1].trim().equals("READ_ONLY") && !parts[1].trim().equals("READ_WRITE")) {
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
        for (int i = 0; i < properties.size(); i++) {
            AConfig entry = properties.get(i);
            for (int j = 0; j < entry.flags.size(); j++) {
                Flag flag = entry.flags.get(j);
                flag.setValue(parse_aconfig_state(textProtoValues.getOrDefault(flag.getName(), String.valueOf(false))));
            }
        }
        for (AConfig aConfigFile : properties) {
            File outputDir = new File(aconfigOutputDir, aConfigFile.packageName.replace(".", "/"));
            outputDir.mkdirs();
            File outputFile = new File(outputDir, "Flags.java");

            StringBuilder classContent = new StringBuilder();
            classContent.append("package ").append(aConfigFile.packageName).append(";\n\n");
            classContent.append("public class Flags {\n");
            for (int i = 0; i < aConfigFile.flags.size(); i++) {
                Flag entry = aConfigFile.flags.get(i);
                StringBuilder method = new StringBuilder();
                method.append("    public static boolean ").append(snakeToCamel(entry.getName())).append("() {\n        return ");
                method.append(entry.getValue());
                method.append(";\n    }\n");
                classContent.append(method.toString());
            }
            classContent.append("}\n");

            try {
                Files.write(outputFile.toPath(), classContent.toString().getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Error writing Flags Java file", e);
            }
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

    public static String toStringReadAllBytes(InputStream stream) throws IOException {
        byte[] stringBytes = stream.readAllBytes();
        return new String(stringBytes);
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


