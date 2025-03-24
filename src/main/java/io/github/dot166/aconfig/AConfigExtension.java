package io.github.dot166.aconfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AConfigExtension {
    /**
     * the aconfig file
     * <p>
     * is normally aconfig/{filename}.aconfig
     */
    public String aconfigFile = "aconfig/config.aconfig";
    /**
     * the repo that contains aconfig textproto
     * <p>
     * the textproto files should be in a different repo than the one the application is in (in AOSP is is <a href="https://cs.android.com/android/platform/superproject/main/+/main:build/release/">platform/build/release</a>)
     */
    public String textProtoRepo = null;
    /**
     * use values in the {reponame}/aconfig/eng folder in debug builds
     * <p>
     * eng is the most debuggable build type in AOSP
     */
    public boolean useENGInDebugBuilds = false;
    /**
     * aconfig file map for textproto files
     * <p>
     * AOSP uses eng/userdebug for debug/development builds, user for release/user builds and uses the first part of the build id (e.g. bp1a, ap4a, etc) as common values for both
     */
    public List<String> commonBuildValues = new ArrayList<>() {{
        add("root"); // root is the base config in AOSP
        add("ap2a"); // ap2a is (part of) the build id for Android 14 QPR3
        add("ap3a"); // ap3a is (part of) the build id for Android 15
        add("ap4a"); // ap4a is (part of) the build id for Android 15 QPR1
        add("bp1a"); // bp1a is (part of) the build id for Android 15 QPR2
    }};
    /**
     * aconfig file map for release/user textproto files
     * <p>
     * this is here to allow for adding custom build folders that are not in <a href="https://cs.android.com/android/platform/superproject/main/+/main:build/release/">platform/build/release</a> that are used in release/user builds
     */
    public List<String> customReleaseBuildValues = new ArrayList<>();
    /**
     * aconfig file map for debug/development textproto files
     * <p>
     * this is here to allow for adding custom build folders that are not in <a href="https://cs.android.com/android/platform/superproject/main/+/main:build/release/">platform/build/release</a> that are used in debug/development builds
     */
    public List<String> customDebugBuildValues = new ArrayList<>();
    /**
     * package name for the flags.java file
     * <p>
     * should be the same here, in the aconfig file and in the textproto file
     */
    public String flagsPackage = null;
}
