// Pure JVM module. Adding any Android dependency here is a build error by design:
// the whole point is that ./gradlew :engine:test runs in milliseconds.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

dependencies {
    api(project(":core-model"))
    testImplementation(libs.kotlin.test)
}
