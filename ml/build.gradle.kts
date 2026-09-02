plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nirog.ml"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(project(":core-model"))
    implementation(project(":engine"))
    implementation(libs.kotlinx.coroutines.core)
    // LiteRT via Play Services (NNAPI is deprecated in Android 15 and never used here)
    implementation(libs.tflite.java)
    implementation(libs.tflite.gpu)
}
