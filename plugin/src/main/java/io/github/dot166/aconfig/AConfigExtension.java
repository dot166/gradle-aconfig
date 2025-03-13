package io.github.dot166.aconfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract class AConfigExtension {
    String aconfigFile = "config.aconfig";
    /**
     * set repo that contains aconfig textproto
     * <p>
     * the textproto files should be in a different repo than the one the application is in (in AOSP is is <a href="https://cs.android.com/android/platform/superproject/main/+/main:build/release/">platform/build/release</a>)
     */
    String textProtoRepo = null;
    Map<String, String> buildTypeMapping = new HashMap<String, String>() {{
        put("debug", "eng"); // eng is the most debuggable build type in AOSP
        put("release", "bp1a"); // bp1a is (part of) the build id for Android 15 QPR2
    }};
    /**
     * package name for the flags.java file
     * <p>
     * should be the same here, in the aconfig file and in the textproto file
     */
    String flagsPackage = null;
}
