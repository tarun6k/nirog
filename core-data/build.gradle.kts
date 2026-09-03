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
    buildFeatures { buildConfig = true }
    defaultConfig {
        // Backend base URL; empty until Phase 7's API is deployed. Empty = sync
        // workers keep rows queued locally and do nothing over the network.
        buildConfigField("String", "NIROG_API_BASE", "\"\"")
    }
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
    implementation(libs.work.runtime)
    ksp(libs.room.compiler)
    coreLibraryDesugaring(libs.desugar.jdk)
    testImplementation(libs.kotlin.test)
    // Android ships org.json at runtime; unit tests only see stubs. Test-classpath only.
    testImplementation("org.json:json:20240303")
}
