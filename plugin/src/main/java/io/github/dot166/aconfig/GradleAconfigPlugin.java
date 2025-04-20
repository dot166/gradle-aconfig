package io.github.dot166.aconfig;

import com.android.build.api.dsl.CommonExtension;
import com.android.build.gradle.AppExtension;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.eclipse.jgit.api.Git;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.TaskProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.artifact.SingleArtifact;

public class GradleAconfigPlugin implements Plugin<Project> {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/dot166/gradle-aconfig/contents/{folder}?ref=main";
    public enum errorCodes {
        Everything_is_Fine,
        Catastrophic_Failure,
        task_ran_manually_with_AGP,
        AGP_Application_not_found
    }

    public File aconfigOutputDir;
    public String ns;
    @Override
    public void apply(Project project) {
        AConfigExtension extension = project.getExtensions().create("aconfig", AConfigExtension.class);
        ApplicationExtension androidComponents =
                project.getExtensions().findByType(ApplicationExtension.class);
        AtomicBoolean debuggable = new AtomicBoolean(false);
        File projectDir = project.getProjectDir();
        File buildDir = project.getBuildDir();
        aconfigOutputDir = new File(buildDir, "generated/source/aconfig/");
        try {
            createLibaconfig(project, extension);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        project.getTasks().register("generateFlags", task -> {
            task.doLast(t -> {
                Map<String, String> properties = new HashMap<String, String>();
                List<String> paths = Arrays.asList(extension.aconfigFiles.split(";"));
                for (int i = 0; i< paths.size(); i++) {
                    File configFile = new File(projectDir, paths.get(i));
                    if (configFile.exists()) {
                        properties.putAll(parseAConfig(configFile));
                    } else {
                        t.getLogger().error("No aconfig file found at {}", paths.get(i));
                    }
                }
                List<File> textProtoFiles = cloneAndFetchTextProtoFiles(t, extension.textProtoRepo, debuggable.get(), extension, buildDir);

                if (!properties.isEmpty()) {
                    List<Flag> resolvedProperties = resolveTextProtoValues(properties, textProtoFiles, extension, t);
                    generateJavaFile(resolvedProperties, extension, buildDir);
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

        project.getPlugins().withId("com.android.application", plugin -> {
            configurePluginForAppOrLibrary(project);
        });

        project.getPlugins().withId("com.android.library", plugin -> {
            configurePluginForAppOrLibrary(project);
        });

        project.afterEvaluate(proj -> {
            Object android = project.getExtensions().findByName("android");
            if (android instanceof CommonExtension) {
                CommonExtension<?, ?, ?, ?, ?, ?> androidExtension = (CommonExtension<?, ?, ?, ?, ?, ?>) android;
                String namespace = androidExtension.getNamespace();
                System.out.println("Namespace: " + namespace);
                ns = namespace;
            } else if (android instanceof AppExtension) {
                // For older AGP: fallback to defaultConfig.getApplicationId()
                AppExtension appExt = (AppExtension) android;
                String appId = appExt.getDefaultConfig().getApplicationId();
                System.out.println("Application ID: " + appId);
                ns = appId;
            }
        });
    }

    private void configurePluginForAppOrLibrary(Project project) {
        AndroidComponentsExtension<?, ?, ?> androidComponents =
                project.getExtensions().getByType(AndroidComponentsExtension.class);

        androidComponents.onVariants(androidComponents.selector().all(), variant -> {
            String taskName = "mergeWithLibaConfigManifest" + variant.getName().toUpperCase();

            TaskProvider<MergeWithLibAConfigManifestTask> mergeTask = project.getTasks().register(
                    taskName,
                    MergeWithLibAConfigManifestTask.class,
                    task -> {
                        File outputFile = new File(
                                project.getBuildDir(),
                                "intermediates/merged_manifests_with_libaconfig/AndroidManifest.xml"
                        );
                        task.getOutputManifest().set(outputFile);

                        task.getInputManifest().set(
                                variant.getArtifacts().get(SingleArtifact.MERGED_MANIFEST.INSTANCE)
                        );
                    }
            );

            variant.getArtifacts().use(mergeTask)
                    .wiredWithFiles(
                            MergeWithLibAConfigManifestTask::getInputManifest,
                            MergeWithLibAConfigManifestTask::getOutputManifest
                    )
                    .toTransform(SingleArtifact.MERGED_MANIFEST.INSTANCE);
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

    private List<Flag> resolveTextProtoValues(Map<String, String> properties, List<File> textProtoFiles, AConfigExtension extension, Task project) {
        Map<String, String> textProtoValues = new HashMap<>();
        Map<String, Boolean> writable = new HashMap<>();
        for (File file : textProtoFiles) {
            String name = null;
            if (file.exists()) {
                try {
                    List<String> lines = Files.readAllLines(file.toPath());
                    for (String line : lines) {
                        if ((line.contains("name: ") || line.contains("package: ") || line.contains("permission: ") || line.contains("state: ")) && !line.startsWith("#")) {
                            String[] parts = line.split(": ", 2);
                            if (!Objects.equals(properties.get(parts[0].trim()), parts[1].trim().replace("\"", "")) && parts[0].trim().equals("package")) {
                                throw new RuntimeException("package name in " + file.getName() + " does not match the package name in one of the following files " + extension.aconfigFiles);
                            } else if (!Objects.equals(properties.get(parts[0].trim()), extension.flagsPackage) && parts[0].trim().equals("package")) {
                                throw new RuntimeException("package name in the config does not match the package name in one of the following files " + extension.aconfigFiles);
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
                                        writable.put(name, false);
                                    } else if (parts[1].trim().equals("READ_WRITE")) {
                                        writable.put(name, true);
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

        List<Flag> resolvedProperties = new ArrayList<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!Objects.equals(entry.getKey(), "package")) {
                resolvedProperties.add(new Flag(entry.getValue(), parse_aconfig_state(textProtoValues.getOrDefault(entry.getValue(), String.valueOf(false))), writable.get(entry.getValue())));
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

    private void generateJavaFile(List<Flag> properties, AConfigExtension extension, File buildDir) {
        File libaconfigDir = new File(buildDir, "libaconfig/java/" + extension.flagsPackage.replace(".", "/"));
        File libaconfigSourceDir = new File(buildDir, "libaconfig/java/io/github/dot166/libaconfig");
        File outputDir = new File(aconfigOutputDir, extension.flagsPackage.replace(".", "/"));
        outputDir.mkdirs();
        libaconfigDir.mkdirs();
        File outputFile = new File(outputDir, "Flags.java");

        StringBuilder classContent = new StringBuilder();
        classContent.append("package ").append(extension.flagsPackage).append(";\n\n");
        classContent.append("import ").append(extension.flagsPackage).append(".writableFlag;\n\n");
        classContent.append("public class Flags {\n");
        for (int i = 0; i < properties.size(); i++) {
            Flag entry = properties.get(i);
            StringBuilder method = new StringBuilder();
            method.append("    public static boolean ").append(snakeToCamel(entry.getKey())).append("() {\n        return ");
            if (!entry.isWritable()) {
                method.append(entry.getValue());
            } else {
                method.append("(new writableFlag(\"").append(entry.getKey()).append("\", ").append(entry.getValue()).append(")).getFlagValue()");
            }
            method.append(";\n    }\n");
            classContent.append(method.toString());
        }
        classContent.append("}\n");

        try {
            Files.write(outputFile.toPath(), classContent.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Error writing Flags Java file", e);
        }
        File libaconfigOutputFile = new File(libaconfigDir, "Keys.java");

        StringBuilder libaconfigClassContent = new StringBuilder();
        libaconfigClassContent.append("package ").append(extension.flagsPackage).append(";\n\n");
        libaconfigClassContent.append("public class Keys {\n");
        libaconfigClassContent.append("    public static String[] keys = {");
        StringBuilder method = new StringBuilder();
        boolean writableFlagExists = false;
        for (int i = 0; i < properties.size(); i++) {
            Flag entry = properties.get(i);
            if (!entry.isWritable()) {
                continue;
            }
            method.append("\"").append(entry.getKey()).append("\", ");
            writableFlagExists = true;
        }
        method.append("};\n");
        if (writableFlagExists) {
            method.replace(method.indexOf(", }"), method.indexOf(", }") + 3, "}");
        }
        libaconfigClassContent.append(method.toString());
        libaconfigClassContent.append("}\n");

        try {
            Files.write(libaconfigOutputFile.toPath(), libaconfigClassContent.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Error writing Keys Java file", e);
        }

        try {
            for (File file : libaconfigSourceDir.listFiles()) {
                Path path0 = file.toPath();
                Path path1 = (new File(libaconfigDir, file.getName())).toPath();
                Charset charset = StandardCharsets.UTF_8;

                String content = new String(Files.readAllBytes(path0), charset);
                content = content.replaceAll("io.github.dot166.libaconfig", extension.flagsPackage);
                if (content.contains(extension.flagsPackage + ".R")) {
                    content = content.replaceAll(extension.flagsPackage + ".R", ns + ".R");
                }
                Files.write(path1, content.getBytes(charset));
                file.delete();
            }
            libaconfigSourceDir.delete();
        } catch (Exception e) {
            throw new RuntimeException("error patching libaconfig", e);
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
    
    private void createLibaconfig(Project project, AConfigExtension extension) throws IOException {
        String libaconfigName = "libaconfig";
        File libaconfigDir = new File(project.getBuildDir(), libaconfigName);
        File libaconfigCacheDir = new File(project.getBuildDir(), "cache/" + libaconfigName);

        // Ensure the subproject directory exists
        if (!libaconfigCacheDir.exists()) {
            libaconfigCacheDir.mkdirs();
        }
        if (libaconfigDir.exists()) {
            FileUtils.copyDirectory(libaconfigDir, libaconfigCacheDir);
        }
        deleteDirectory(libaconfigDir);//libaconfigDir.delete();
        libaconfigDir.mkdirs();
        downloadLibaconfig(project, extension);
        deleteDirectory(libaconfigCacheDir);//libaconfigCacheDir.delete();

        // manually recreate libaconfig build files and merge them with the project
        // this is done because for some stupid reason libaconfig cannot be added as a submodule dynamically
        if (project.getPlugins().hasPlugin("com.android.base")) {

            // Configure AGP source sets to include generated sources
            project.getExtensions().configure(BaseExtension.class, android -> {
                android.getSourceSets().getByName("main").getJava().srcDir(new File(libaconfigDir, "java"));
                android.getSourceSets().getByName("main").getRes().srcDir(new File(libaconfigDir, "res"));
            });
            if (!project.getRootProject().getName().equals("j-Lib")) { // libaconfig for android relies on jLib for preference menu things, prevent jLib from importing itself, do not want to know what would happen if it did import itself
                project.getDependencies().add("api", "io.github.dot166:j-Lib:+");
            }
        } else if (project.getPlugins().hasPlugin("org.gradle.java")) {
            project.getExtensions().getByType(JavaPluginExtension.class)
                    .getSourceSets().getByName("main")
                    .getJava().srcDir(new File(libaconfigDir, "java"));
            project.getExtensions().getByType(JavaPluginExtension.class)
                    .getSourceSets().getByName("main")
                    .getResources().srcDir(new File(libaconfigDir, "resources"));
        }
    }

    private void downloadLibaconfig(Project project, AConfigExtension extension) {
        String dir = project.getPlugins().hasPlugin("com.android.base") ? "android" : "java";

        String folder = "libaconfig/" + dir + "/src/main"; // Path inside repo
        String apiUrl = GITHUB_API_URL.replace("{folder}", folder);

        try {
            project.getLogger().lifecycle("Downloading libaconfig from GitHub.");
            downloadGitHubFolder(apiUrl, project.getBuildDir().getAbsolutePath() + "/libaconfig", dir, project, extension);
            project.getLogger().lifecycle("libaconfig downloaded successfully.");
        } catch (Exception e) {
            project.getLogger().lifecycle(e.toString());
            project.getLogger().lifecycle(Arrays.toString(e.getStackTrace()));
            String libaconfigName = "libaconfig";
            File libaconfigDir = new File(project.getBuildDir(), libaconfigName);
            File libaconfigCacheDir = new File(project.getBuildDir(), "cache/" + libaconfigName);
            if (libaconfigCacheDir.exists()) {
                try {
                    project.getLogger().lifecycle("Failed to download libaconfig from GitHub.");
                    project.getLogger().lifecycle("Using cached libaconfig");
                    FileUtils.copyDirectory(libaconfigCacheDir, libaconfigDir);
                    project.getLogger().lifecycle("libaconfig copied successfully.");
                } catch (Exception e1) {
                    project.getLogger().lifecycle(e1.toString());
                    project.getLogger().lifecycle(Arrays.toString(e1.getStackTrace()));
                    throw new RuntimeException("Failed to obtain libaconfig", e1);
                }
            } else {
                project.getLogger().lifecycle("ERROR: No cached libaconfig found.");
                throw new RuntimeException("Failed to download libaconfig", e);
            }
        }
    }

    private void downloadGitHubFolder(String apiUrl, String destination, String buildtypedir, Project project, AConfigExtension extension) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("GitHub API request failed. Response Code: " + responseCode);
        }

        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream());
             BufferedReader br = new BufferedReader(reader)) {

            // ✅ Log API response for debugging
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                responseBuilder.append(line);
            }
            String jsonResponse = responseBuilder.toString();
            project.getLogger().debug("API Response: " + jsonResponse);

            JsonArray files = JsonParser.parseString(jsonResponse).getAsJsonArray();

            for (JsonElement element : files) {
                JsonObject fileObj = element.getAsJsonObject();
                String type = fileObj.get("type").getAsString();
                String path = fileObj.get("path").getAsString();
                String downloadUrl = existsAndNotNull(fileObj) ? fileObj.get("download_url").getAsString() : null;

                String filePath = destination + path.replace("libaconfig/" + buildtypedir + "/src/main/", "/");
                if ("file".equals(type) && downloadUrl != null) {
                    project.getLogger().debug("Downloading file: " + path);
                    downloadFile(downloadUrl, filePath, project);
                } else if ("dir".equals(type)) {
                    FileUtils.forceMkdir(new File(filePath));
                    String subfolderApiUrl = GITHUB_API_URL.replace("{folder}", path);
                    downloadGitHubFolder(subfolderApiUrl, destination, buildtypedir, project, extension);
                }
            }
        }
    }

    private void downloadFile(String fileUrl, String destination, Project project) throws IOException {
        try {
            URL url = new URL(fileUrl);
            FileUtils.forceMkdirParent(new File(destination));

            try (InputStream in = url.openStream();
                 FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            project.getLogger().debug("Saved: " + destination);
        } catch (Exception e) {
            project.getLogger().error("Error downloading file: " + fileUrl, e);
        }
    }

    private boolean existsAndNotNull(JsonObject fileObj) {
        if (fileObj.has("download_url")) {
            // null check
            // do this instead of using the isJsonNull() method because for some reason it returns false when it is a null object (when the object is JsonNull) (see https://api.github.com/repos/dot166/gradle-aconfig/contents/libaconfig/android?ref=main for what i mean), google fix this
            return !"dir".equals(fileObj.get("type").getAsString());
            //return !fileObj.isJsonNull(); // old null check
        } else {
            return false;
        }
    }

}


