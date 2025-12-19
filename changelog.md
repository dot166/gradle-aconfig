# v1.2.0

 - complete kotlin rewrite
 - fix debuggable parsing for android libraries (finally)
 - support kotlin projects properly
 - do not allow read-write flags if AOSP doesn't allow them, uses the flag RELEASE_ACONFIG_REQUIRE_ALL_READ_ONLY in flag_values/bp1a/RELEASE_ACONFIG_REQUIRE_ALL_READ_ONLY.textproto, like how AOSP does
