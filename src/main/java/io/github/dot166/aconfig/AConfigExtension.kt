package io.github.dot166.aconfig

open class AConfigExtension {
    /**
     * the aconfig files
     *
     *
     * is normally aconfig/{filename}.aconfig
     */
    @JvmField
    var aconfigFiles: MutableList<String> = mutableListOf("aconfig/config.aconfig")

    /**
     * the repo that contains aconfig textproto
     *
     *
     * the textproto files should be in a different repo than the one the application is in (in AOSP is is [platform/build/release](https://cs.android.com/android/platform/superproject/+/android-latest-release:build/release/)) if the isAOSP config flag is true (it is by default)
     */
    @JvmField
    var textProtoRepo: String? = null

    /**
     * aconfig file map for release/user textproto files
     *
     *
     * this is here to allow for adding custom build folders that are not in [platform/build/release](https://cs.android.com/android/platform/superproject/+/android-latest-release:build/release/) that are used in release/user builds
     */
    @JvmField
    var customReleaseBuildValues: MutableList<String> = mutableListOf()

    /**
     * aconfig file map for debug/development textproto files
     *
     *
     * this is here to allow for adding custom build folders that are not in [platform/build/release](https://cs.android.com/android/platform/superproject/+/android-latest-release:build/release/) that are used in debug/development builds
     */
    @JvmField
    var customDebugBuildValues: MutableList<String> = mutableListOf()

    /**
     * boolean config flag that tells gradle-aconfig if this is an AOSP project
     */
    @JvmField
    var isAOSP: Boolean = true
}
