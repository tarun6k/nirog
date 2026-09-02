pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "nirog"
include(
    ":core-model",
    ":core-data",
    ":engine",
    ":ml",
    ":feature-scan",
    ":feature-diagnosis",
    ":feature-treatment",
    ":feature-diary",
    ":app",
)
