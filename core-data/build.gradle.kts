plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nirog.data"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":core-model"))
    api(project(":engine"))
    api(libs.kotlinx.coroutines.core)
    api(libs.room.runtime) // NirogDb extends RoomDatabase, so the type is part of this module's API
    implementation(libs.room.ktx)
    implementation(libs.okhttp)
    ksp(libs.room.compiler)
    coreLibraryDesugaring(libs.desugar.jdk)
    testImplementation(libs.kotlin.test)
}
