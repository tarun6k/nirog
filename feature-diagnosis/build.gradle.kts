plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nirog.feature.diagnosis"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(project(":core-data"))
    api(project(":ml"))
    implementation(project(":engine"))
    implementation(libs.kotlinx.coroutines.core)
    coreLibraryDesugaring(libs.desugar.jdk)
}
