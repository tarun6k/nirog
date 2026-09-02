plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nirog.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nirog.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core-data"))
    implementation(project(":engine"))
    implementation(project(":ml"))
    implementation(project(":feature-scan"))
    implementation(project(":feature-diagnosis"))
    implementation(project(":feature-treatment"))
    implementation(project(":feature-diary"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    coreLibraryDesugaring(libs.desugar.jdk)
}
