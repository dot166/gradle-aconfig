plugins {
    id("com.android.library") version "8.9.1"
}

android {
    namespace = "io.github.dot166.libaconfig"
    compileSdk = 35
}

dependencies {
    //noinspection GradleDynamicVersion
    implementation("io.github.dot166:j-Lib:4.1.2") // you might be thinking, 'wouldn't this cause a circle dependency', no it wont because manifests, resources and code is not merged in android libraries in gradle unless the dependency is 'api' and jLib is 'implementation' and also this is using the latest stable and not the developer version that this library would be compiling with
}